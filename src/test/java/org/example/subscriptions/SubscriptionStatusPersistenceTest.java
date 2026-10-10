package org.example.subscriptions;

import org.example.subscriptions.catalogs.Client;
import org.example.subscriptions.documents.Subscription;
import org.example.subscriptions.enums.SubscriptionStatus;
import org.example.subscriptions.jobs.SubscriptionStatusJob;
import org.example.subscriptions.repositories.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import su.onno.types.Ref;
import su.onno.validation.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionStatusPersistenceTest extends ApiTestSupport {
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private SubscriptionStatusJob job;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @ParameterizedTest(name = "Status refresh must not allow changing {0}")
    @MethodSource("purchaseMutations")
    void statusRefreshDoesNotAllowOtherChanges(String name, Consumer<Subscription> mutate) throws Exception {
        UUID id = postedSubscription();
        var before = getJson(path(id));
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            var sub = subscriptions.findLockedByIdAndDeletionMarkFalse(id).orElseThrow();
            assertThat(sub.refreshStatus(sub.getStartDate())).isTrue();
            mutate.accept(sub);
            subscriptions.save(sub);
        })).isInstanceOf(ValidationException.class);
        assertThat(getJson(path(id))).isEqualTo(before);
    }

    static Stream<Arguments> purchaseMutations() {
        return Stream.of(
                Arguments.of("client", (Consumer<Subscription>) s -> s.setClient(new Ref<>(Client.class, UUID.randomUUID()))),
                Arguments.of("total", (Consumer<Subscription>) s -> s.setTotal(BigDecimal.ONE)),
                Arguments.of("start date", (Consumer<Subscription>) s -> s.setStartDate(s.getStartDate().minusDays(1))),
                Arguments.of("end date", (Consumer<Subscription>) s -> s.setEndDate(s.getEndDate().plusDays(1))),
                Arguments.of("line price", (Consumer<Subscription>) s -> s.getLines().getFirst().setPrice(BigDecimal.ONE)),
                Arguments.of("line periods", (Consumer<Subscription>) s -> s.getLines().getFirst().setPeriods(99)),
                Arguments.of("cancellation", (Consumer<Subscription>) s -> s.setStatus(SubscriptionStatus.CANCELLED)),
                Arguments.of("posted flag", (Consumer<Subscription>) s -> s.setPosted(false))
        );
    }

    @Test
    void refreshRejectsAPurchaseModifiedBeforeTheCall() throws Exception {
        var sub = subscriptions.findById(postedSubscription()).orElseThrow();
        sub.getLines().getFirst().setAmount(BigDecimal.ONE);
        assertThatThrownBy(() -> sub.refreshStatus(sub.getStartDate())).isInstanceOf(ValidationException.class);
    }

    @Test
    void plainRepositorySaveCannotChangePostedStatus() throws Exception {
        UUID id = postedSubscription();
        var sub = subscriptions.findById(id).orElseThrow();
        sub.setStatus(SubscriptionStatus.CANCELLED);
        assertThatThrownBy(() -> subscriptions.save(sub)).isInstanceOf(ValidationException.class);
        assertThat(getJson(path(id)).path("statusDisplay").asText()).isEqualTo("Запланирована");
    }

    @Test
    void unpostingWaitsForStatusTransactionAndIsNotOverwritten() throws Exception {
        UUID id = postedSubscription();
        String client = getJson(path(id)).path("client").asText();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var unpostStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var refresh = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                var sub = subscriptions.findLockedByIdAndDeletionMarkFalse(id).orElseThrow();
                locked.countDown();
                await(release);
                assertThat(sub.refreshStatus(sub.getStartDate())).isTrue();
                subscriptions.save(sub);
            }));
            try {
                await(locked);
                var unpost = executor.submit(() -> {
                    unpostStarted.countDown();
                    return request("POST", path(id) + "/unpost", null);
                });
                await(unpostStarted);
                assertThatThrownBy(() -> unpost.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                refresh.get(10, TimeUnit.SECONDS);
                var response = unpost.get(10, TimeUnit.SECONDS);
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            } finally {
                release.countDown();
            }
        }
        assertThat(getJson(path(id)).path("posted").asBoolean()).isFalse();
        job.execute();
        assertThat(getJson(path(id)).path("statusDisplay").asText()).isEqualTo("Черновик");
        assertThat(getJson("/api/registers/AccountBalance/balance?client=" + client).get(0).path("amount").decimalValue())
                .isEqualByComparingTo("100");
        assertThat(getJson("/api/registers/TariffRevenue/turnover?from=2000-01-01T00:00:00&to=2100-01-01T00:00:00&client=" + client).size())
                .isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private UUID postedSubscription() throws Exception {
        String client = createAt("/api/catalogs/Clients", Map.of("description", "Защита покупки")).path("id").asText();
        String tariff = createAt("/api/catalogs/Tariffs", Map.of("description", "Тариф", "price", "100", "periodDays", 30)).path("id").asText();
        var payment = createAt("/api/documents/Payments", Map.of("client", client, "amount", "100"));
        post("/api/documents/Payments/" + payment.path("id").asText());
        var sub = createAt("/api/documents/Subscriptions", Map.of("client", client, "startDate", LocalDate.now().plusDays(5).toString(),
                "lines", List.of(Map.of("tariff", tariff, "periods", 1))));
        UUID id = UUID.fromString(sub.path("id").asText());
        post(path(id));
        return id;
    }

    private String path(UUID id) {
        return "/api/documents/Subscriptions/" + id;
    }

    private void post(String path) throws Exception {
        var response = request("POST", path + "/post", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }
}
