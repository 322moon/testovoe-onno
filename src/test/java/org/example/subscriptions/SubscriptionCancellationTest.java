package org.example.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.subscriptions.jobs.SubscriptionStatusJob;
import org.example.subscriptions.services.SubscriptionCancellationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionCancellationTest extends ApiTestSupport {
    @Autowired
    private SubscriptionCancellationService cancellation;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private SubscriptionStatusJob job;

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "cancelSubscription"})
    void cancelsFromListAndFormAndPreservesPurchaseWithUnavailableTariff(String action) throws Exception {
        var purchase = purchase(true);
        var before = getJson(purchase.path());
        ok("PUT", "/api/catalogs/Tariffs/" + purchase.tariff(),
                Map.of("available", false, "price", "900", "periodDays", 1));
        var response = cancel(purchase, action, "  Клиент попросил отменить  ");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("refresh").asBoolean()).isTrue();
        var after = getJson(purchase.path());
        assertThat(after.path("posted").asBoolean()).isFalse();
        assertThat(after.path("statusDisplay").asText()).isEqualTo("Отменена");
        assertThat(after.path("cancellationReason").asText()).isEqualTo("Клиент попросил отменить");
        for (String field : List.of("client", "date", "number", "startDate", "endDate", "total")) {
            assertThat(after.path(field)).as(field).isEqualTo(before.path(field));
        }
        assertThat(withoutLineNumbers(after.path("lines"))).isEqualTo(withoutLineNumbers(before.path("lines")));
        assertThat(balance(purchase)).isEqualByComparingTo("500");
        assertThat(revenue(purchase)).isEmpty();
        job.execute();
        assertThat(getJson(purchase.path()).path("statusDisplay").asText()).isEqualTo("Отменена");
        assertThat(cancel(purchase, action, "Другая причина").statusCode()).isEqualTo(409);
        assertThat(getJson(purchase.path()).path("cancellationReason").asText()).isEqualTo("Клиент попросил отменить");
    }

    @Test
    void draftCancellationCreatesNoMovementsEvenWhenLaterPosted() throws Exception {
        var draft = purchase(false);
        assertThat(cancel(draft, "cancel", "Передумал").statusCode()).isEqualTo(200);
        ok("POST", draft.path() + "/post", null);
        assertThat(balance(draft)).isEqualByComparingTo("500");
        assertThat(revenue(draft)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void invalidReasonRollsBackUnposting(String reason) throws Exception {
        var purchase = purchase(true);
        var before = getJson(purchase.path());
        var revenue = revenue(purchase);
        var response = cancel(purchase, "cancel", reason);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
        assertThat(getJson(purchase.path())).isEqualTo(before);
        assertThat(balance(purchase)).isEqualByComparingTo("300");
        assertThat(revenue(purchase)).isEqualTo(revenue);
    }

    @Test
    void rejectsOverlongReasonWithoutRemovingMovements() throws Exception {
        var purchase = purchase(true);
        assertThat(cancel(purchase, "cancel", "x".repeat(1001)).statusCode()).isEqualTo(422);
        assertThat(getJson(purchase.path()).path("posted").asBoolean()).isTrue();
        assertThat(balance(purchase)).isEqualByComparingTo("300");
    }

    @Test
    void rollbackAfterCancellationRestoresStatusReasonAndBothRegisters() throws Exception {
        var purchase = purchase(true);
        var before = getJson(purchase.path());
        var revenue = revenue(purchase);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            cancellation.cancel(purchase.id(), "Проверка отката");
            throw new IllegalStateException("Failure after saving cancellation");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(getJson(purchase.path())).isEqualTo(before);
        assertThat(balance(purchase)).isEqualByComparingTo("300");
        assertThat(revenue(purchase)).isEqualTo(revenue);
    }

    @Test
    void deletedSubscriptionCannotBeCancelled() throws Exception {
        var draft = purchase(false);
        ok("DELETE", draft.path(), null);
        assertThat(cancel(draft, "cancel", "Удалённый документ").statusCode()).isEqualTo(422);
        assertThat(balance(draft)).isEqualByComparingTo("500");
    }

    private java.net.http.HttpResponse<String> cancel(Purchase purchase, String action, String reason) throws Exception {
        return request("POST", "/api/actions/documents/Subscriptions/" + action + "?id=" + purchase.id(),
                Map.of("inputs", Map.of("reason", reason)));
    }

    private Purchase purchase(boolean posted) throws Exception {
        String client = createAt("/api/catalogs/Clients", Map.of("description", "Отмена подписки")).path("id").asText();
        String tariff = createAt("/api/catalogs/Tariffs", Map.of("description", "Месяц", "price", "100", "periodDays", 30)).path("id").asText();
        var payment = createAt("/api/documents/Payments", Map.of("client", client, "amount", "500"));
        ok("POST", "/api/documents/Payments/" + payment.path("id").asText() + "/post", null);
        var subscription = createAt("/api/documents/Subscriptions", Map.of("client", client,
                "lines", List.of(Map.of("tariff", tariff, "periods", 2))));
        var purchase = new Purchase(UUID.fromString(subscription.path("id").asText()), client, tariff);
        if (posted) ok("POST", purchase.path() + "/post", null);
        return purchase;
    }

    private java.math.BigDecimal balance(Purchase purchase) throws Exception {
        return getJson("/api/registers/AccountBalance/balance?client=" + purchase.client()).get(0).path("amount").decimalValue();
    }

    private JsonNode revenue(Purchase purchase) throws Exception {
        return getJson("/api/registers/TariffRevenue/turnover?from=2000-01-01T00:00:00&to=2100-01-01T00:00:00&client=" + purchase.client());
    }

    private JsonNode withoutLineNumbers(JsonNode lines) {
        JsonNode copy = lines.deepCopy();
        copy.forEach(row -> ((ObjectNode) row).remove("lineNumber"));
        return copy;
    }

    private void ok(String method, String path, Map<String, Object> body) throws Exception {
        var response = request(method, path, body);
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 204);
    }

    private record Purchase(UUID id, String client, String tariff) {
        String path() { return "/api/documents/Subscriptions/" + id; }
    }
}
