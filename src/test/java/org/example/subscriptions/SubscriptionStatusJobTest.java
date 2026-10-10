package org.example.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.subscriptions.enums.SubscriptionStatus;
import org.example.subscriptions.jobs.SubscriptionStatusJob;
import org.example.subscriptions.repositories.SubscriptionRepository;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import su.onno.repository.EnumerationPersistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionStatusJobTest extends ApiTestSupport {
    @Autowired
    private SubscriptionStatusJob job;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private StorageProvider jobs;

    @Test
    void registersRecurringTaskWithOnno() {
        assertThat(jobs.getRecurringJobs()).anySatisfy(recurring -> {
            assertThat(recurring.getId()).isEqualTo("subscription-statuses");
            assertThat(recurring.getScheduleExpression()).isEqualTo("* * * * *");
        });
    }

    @Test
    void updatesAtDateBoundariesWithoutRepricingOrChangingMoney() throws Exception {
        String client = client();
        String tariff = tariff();
        LocalDate start = LocalDate.now().plusDays(5);
        var payment = createAt("/api/documents/Payments", Map.of("client", client, "amount", "200"));
        ok("POST", "/api/documents/Payments/" + payment.path("id").asText() + "/post", null);
        String path = subscription(client, tariff, start, false);
        ok("POST", path + "/post", null);
        var purchase = getJson(path);
        var balance = getJson("/api/registers/AccountBalance/balance?client=" + client);
        String turnover = "/api/registers/TariffRevenue/turnover?from=2000-01-01T00:00:00&to=2100-01-01T00:00:00&client=" + client;
        var revenue = getJson(turnover);
        ok("PUT", "/api/catalogs/Tariffs/" + tariff, Map.of("price", "999", "periodDays", 1, "available", false));

        job.refresh(start.minusDays(1));
        assertStatus(path, "Запланирована");
        job.refresh(start);
        assertStatus(path, "Активна");
        job.refresh(start.plusDays(29));
        assertStatus(path, "Активна");
        job.refresh(start.plusDays(30));
        assertStatus(path, "Истекла");
        assertThat(job.refresh(start.plusDays(30))).isZero();

        var after = getJson(path);
        for (String field : List.of("client", "date", "startDate", "endDate", "total", "posted")) {
            assertThat(after.path(field)).as(field).isEqualTo(purchase.path(field));
        }
        // REST writes one-based line numbers; Spring Data JDBC uses zero-based list indexes.
        // Stable row IDs, their order, refs and all purchase values must survive the repository save.
        assertThat(withoutLineNumbers(after.path("lines"))).isEqualTo(withoutLineNumbers(purchase.path("lines")));
        assertThat(getJson(turnover)).isEqualTo(revenue);
        assertThat(getJson("/api/registers/AccountBalance/balance?client=" + client)).isEqualTo(balance);
        // Undoing the purchase must not leave the draft active/expired on the next tick.
        ok("POST", path + "/unpost", null);
        job.execute();
        assertStatus(path, "Черновик");
    }

    @Test
    void skipsCancelledAndDeletedDocumentsAndDoesNotActivateDrafts() throws Exception {
        String client = client();
        String tariff = tariff();
        LocalDate start = LocalDate.now().minusDays(40);
        String draft = subscription(client, tariff, start, false);
        String cancelled = subscription(client, tariff, start, true);
        ok("POST", cancelled + "/post", null);
        String deleted = subscription(client, tariff, start, false);
        ok("DELETE", deleted, null);
        UUID deletedId = UUID.fromString(deleted.substring(deleted.lastIndexOf('/') + 1));

        job.execute();
        assertStatus(draft, "Черновик");
        assertStatus(cancelled, "Отменена");
        var stored = subscriptions.findById(deletedId).orElseThrow();
        assertThat(stored.isDeletionMark()).isTrue();
        assertThat(stored.getStatus()).isEqualTo(SubscriptionStatus.DRAFT);
    }

    private JsonNode withoutLineNumbers(JsonNode lines) {
        JsonNode copy = lines.deepCopy();
        copy.forEach(row -> ((ObjectNode) row).remove("lineNumber"));
        return copy;
    }

    private String subscription(String client, String tariff, LocalDate start, boolean cancelled) throws Exception {
        var created = createAt("/api/documents/Subscriptions", Map.of("client", client, "startDate", start.toString(),
                "status", EnumerationPersistence.resolveId(SubscriptionStatus.class,
                        cancelled ? SubscriptionStatus.CANCELLED : SubscriptionStatus.DRAFT).toString(),
                "lines", List.of(Map.of("tariff", tariff, "periods", 1), Map.of("tariff", tariff, "periods", 1))));
        return "/api/documents/Subscriptions/" + created.path("id").asText();
    }

    private String client() throws Exception {
        return createAt("/api/catalogs/Clients", Map.of("description", "Проверка регламентного задания")).path("id").asText();
    }

    private String tariff() throws Exception {
        return createAt("/api/catalogs/Tariffs", Map.of("description", "Месяц", "price", "100", "periodDays", 30)).path("id").asText();
    }

    private void assertStatus(String path, String expected) throws Exception {
        assertThat(getJson(path).path("statusDisplay").asText()).isEqualTo(expected);
    }

    private void ok(String method, String path, Map<String, Object> body) throws Exception {
        var response = request(method, path, body);
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 204);
    }
}
