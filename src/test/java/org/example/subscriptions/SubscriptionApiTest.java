package org.example.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.subscriptions.enums.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import su.onno.repository.EnumerationPersistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionApiTest extends ApiTestSupport {
    @Test
    void calculatesPricesAmountsAndLongestDurationOnEverySave() throws Exception {
        String client = client();
        String monthly = tariff("100.25", 30);
        String annual = tariff("1000", 365);
        var created = createAt("/api/documents/Subscriptions", Map.of(
                "client", client, "startDate", "2026-01-01", "total", "1", "endDate", "2026-01-02",
                "lines", List.of(Map.of("tariff", monthly, "periods", 2, "price", "1", "amount", "1"),
                        line(annual, 1))));
        String path = path(created);
        var stored = getJson(path);
        assertThat(stored.path("statusDisplay").asText()).isEqualTo("Черновик");
        assertThat(stored.path("date").asText()).isNotBlank();
        assertThat(stored.path("endDate").asText()).isEqualTo("2027-01-01");
        assertThat(stored.path("total").decimalValue()).isEqualByComparingTo("1200.50");
        assertThat(stored.path("lines").get(0).path("price").decimalValue()).isEqualByComparingTo("100.25");
        assertThat(stored.path("lines").get(0).path("amount").decimalValue()).isEqualByComparingTo("200.50");
        assertThat(balance(client)).isZero();
        assertThat(revenue(client, null, "amount")).isZero();

        ok("PUT", "/api/catalogs/Tariffs/" + monthly, Map.of("price", "200", "periodDays", 45));
        ok("PUT", path, Map.of("lines", List.of(line(monthly, 3))));
        stored = getJson(path);
        assertThat(stored.path("lines").size()).isEqualTo(1);
        assertThat(stored.path("total").decimalValue()).isEqualByComparingTo("600");
        assertThat(stored.path("endDate").asText()).isEqualTo("2026-05-16");
    }

    @Test
    void defaultsStartDateAndKeepsItOnPartialUpdate() throws Exception {
        String tariff = tariff("10", 30);
        LocalDate before = LocalDate.now();
        var created = subscription(client(), List.of(line(tariff, 1)));
        String start = getJson(path(created)).path("startDate").asText();
        assertThat(LocalDate.parse(start)).isBetween(before, LocalDate.now());
        ok("PUT", path(created), Map.of("lines", List.of(line(tariff, 2))));
        var stored = getJson(path(created));
        assertThat(stored.path("startDate").asText()).isEqualTo(start);
        assertThat(stored.path("endDate").asText()).isEqualTo(LocalDate.parse(start).plusDays(60).toString());
    }

    @Test
    void postingDebitsOnlyOwnerAndRecordsRevenueByTariffThenUnpostRestoresBoth() throws Exception {
        String client = client();
        String other = client();
        String a = tariff("100", 30);
        String b = tariff("50", 10);
        topUp(client, "1000");
        var created = subscription(client, List.of(line(a, 2), line(b, 3)));
        String path = path(created);
        ok("POST", path + "/post", null);
        assertThat(balance(client)).isEqualByComparingTo("650");
        assertThat(balance(other)).isZero();
        assertThat(revenue(client, a, "amount")).isEqualByComparingTo("200");
        assertThat(revenue(client, a, "periods")).isEqualByComparingTo("2");
        assertThat(revenue(client, b, "amount")).isEqualByComparingTo("150");
        assertThat(revenue(other, null, "amount")).isZero();
        assertThat(getJson(path).path("statusDisplay").asText()).isEqualTo("Активна");

        var repeated = request("POST", path + "/post", null);
        assertThat(repeated.statusCode()).as(repeated.body()).isEqualTo(400);
        var edited = request("PUT", path, Map.of("client", other));
        assertThat(edited.statusCode()).as(edited.body()).isEqualTo(400);
        assertThat(balance(client)).isEqualByComparingTo("650");
        assertThat(revenue(client, null, "amount")).isEqualByComparingTo("350");
        ok("POST", path + "/unpost", null);
        assertThat(balance(client)).isEqualByComparingTo("1000");
        assertThat(revenue(client, null, "amount")).isZero();
        assertThat(revenue(client, null, "periods")).isZero();
    }

    @Test
    void insufficientFundsRollBackPostingAndAllMovements() throws Exception {
        String client = client();
        String tariff = tariff("100", 30);
        topUp(client, "199");
        String path = path(subscription(client, List.of(line(tariff, 2))));
        var denied = request("POST", path + "/post", null);
        // onno 3.4.1 reports its balance violation as HTTP 500; rollback is still mandatory.
        assertThat(denied.statusCode()).as(denied.body()).isEqualTo(500);
        assertThat(getJson(path).path("posted").asBoolean()).isFalse();
        assertThat(balance(client)).isEqualByComparingTo("199");
        assertThat(revenue(client, tariff, "amount")).isZero();
        assertThat(revenue(client, tariff, "periods")).isZero();
        topUp(client, "1");
        ok("POST", path + "/post", null);
        assertThat(balance(client)).isZero();
        assertThat(revenue(client, tariff, "amount")).isEqualByComparingTo("200");
    }

    @Test
    void spentPaymentCannotBeUnposted() throws Exception {
        String client = client();
        String payment = topUp(client, "100");
        String subscription = path(subscription(client, List.of(line(tariff("100", 30), 1))));
        ok("POST", subscription + "/post", null);
        var denied = request("POST", payment + "/unpost", null);
        // onno 3.4.1 reports its balance violation as HTTP 500; rollback is still mandatory.
        assertThat(denied.statusCode()).as(denied.body()).isEqualTo(500);
        assertThat(getJson(payment).path("posted").asBoolean()).isTrue();
        assertThat(balance(client)).isZero();
        assertThat(revenue(client, null, "amount")).isEqualByComparingTo("100");
    }

    @Test
    void cancelledDocumentMakesNoMovementsWithoutRequiringFunds() throws Exception {
        String client = client();
        var created = createAt("/api/documents/Subscriptions", Map.of("client", client,
                "status", statusId(SubscriptionStatus.CANCELLED), "lines", List.of(line(tariff("100", 30), 2))));
        ok("POST", path(created) + "/post", null);
        assertThat(getJson(path(created)).path("statusDisplay").asText()).isEqualTo("Отменена");
        assertThat(balance(client)).isZero();
        assertThat(revenue(client, null, "amount")).isZero();
        assertThat(revenue(client, null, "periods")).isZero();
    }

    @Test
    void cancellingPostedDocumentRequiresUnpostAndRemovesRevenue() throws Exception {
        String client = client();
        topUp(client, "100");
        String path = path(subscription(client, List.of(line(tariff("100", 30), 1))));
        ok("POST", path + "/post", null);
        var denied = request("PUT", path, Map.of("status", statusId(SubscriptionStatus.CANCELLED)));
        assertThat(denied.statusCode()).as(denied.body()).isEqualTo(400);
        assertThat(revenue(client, null, "amount")).isEqualByComparingTo("100");
        ok("POST", path + "/unpost", null);
        ok("PUT", path, Map.of("status", statusId(SubscriptionStatus.CANCELLED)));
        ok("POST", path + "/post", null);
        assertThat(balance(client)).isEqualByComparingTo("100");
        assertThat(revenue(client, null, "amount")).isZero();
    }

    @Test
    void rechecksPriceAndAvailabilityWhenPosting() throws Exception {
        String client = client();
        topUp(client, "500");
        String tariff = tariff("100", 30);
        String path = path(subscription(client, List.of(line(tariff, 1))));
        ok("PUT", "/api/catalogs/Tariffs/" + tariff, Map.of("available", false));
        var denied = request("POST", path + "/post", null);
        assertThat(denied.statusCode()).as(denied.body()).isEqualTo(400);
        assertThat(balance(client)).isEqualByComparingTo("500");
        ok("PUT", "/api/catalogs/Tariffs/" + tariff, Map.of("available", true, "price", "200"));
        ok("POST", path + "/post", null);
        assertThat(getJson(path).path("total").decimalValue()).isEqualByComparingTo("200");
        assertThat(balance(client)).isEqualByComparingTo("300");
        assertThat(revenue(client, tariff, "amount")).isEqualByComparingTo("200");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositivePeriods(int periods) throws Exception {
        var response = request("POST", "/api/documents/Subscriptions", Map.of("client", client(),
                "lines", List.of(line(tariff("10", 30), periods))));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
    }

    @Test
    void requiresClientLinesAndTariff() throws Exception {
        String client = client();
        List<Map<String, Object>> validLines = List.of(line(tariff("10", 30), 1));
        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("lines", validLines), Map.of("client", client),
                Map.of("client", client, "lines", List.of()),
                Map.of("client", client, "lines", List.of(Map.of("periods", 1))),
                Map.of("client", client, "lines", List.of(Map.of("tariff", UUID.randomUUID().toString(), "periods", 1))),
                Map.of("client", client, "lines", List.of(Map.of("tariff", validLines.getFirst().get("tariff")))))) {
            var response = request("POST", "/api/documents/Subscriptions", body);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        }
    }

    @Test
    void rejectsUnavailableAndDeletedTariffs() throws Exception {
        String client = client();
        String tariff = tariff("10", 30);
        ok("PUT", "/api/catalogs/Tariffs/" + tariff, Map.of("available", false));
        var unavailable = request("POST", "/api/documents/Subscriptions", Map.of("client", client, "lines", List.of(line(tariff, 1))));
        assertThat(unavailable.statusCode()).as(unavailable.body()).isEqualTo(400);
        ok("PUT", "/api/catalogs/Tariffs/" + tariff, Map.of("available", true));
        ok("DELETE", "/api/catalogs/Tariffs/" + tariff, null);
        var deleted = request("POST", "/api/documents/Subscriptions", Map.of("client", client, "lines", List.of(line(tariff, 1))));
        assertThat(deleted.statusCode()).as(deleted.body()).isEqualTo(400);
    }

    @Test
    void freeTariffCanBePostedWithZeroBalance() throws Exception {
        String client = client();
        String tariff = tariff("0", 30);
        String path = path(subscription(client, List.of(line(tariff, 2))));
        ok("POST", path + "/post", null);
        assertThat(balance(client)).isZero();
        assertThat(revenue(client, tariff, "amount")).isZero();
        assertThat(revenue(client, tariff, "periods")).isEqualByComparingTo("2");
    }

    @Test
    void postingDistinguishesFutureStartAndExclusiveEnd() throws Exception {
        String client = client();
        String tariff = tariff("0", 1);
        for (var entry : Map.of(LocalDate.now().plusDays(1), "Запланирована", LocalDate.now().minusDays(1), "Истекла").entrySet()) {
            var created = createAt("/api/documents/Subscriptions", Map.of("client", client,
                    "startDate", entry.getKey().toString(), "lines", List.of(line(tariff, 1))));
            ok("POST", path(created) + "/post", null);
            assertThat(getJson(path(created)).path("statusDisplay").asText()).isEqualTo(entry.getValue());
        }
    }

    private JsonNode subscription(String client, List<Map<String, Object>> lines) throws Exception {
        return createAt("/api/documents/Subscriptions", Map.of("client", client, "lines", lines));
    }

    private String client() throws Exception {
        return createAt("/api/catalogs/Clients", Map.of("description", "Клиент подписки")).path("id").asText();
    }

    private String tariff(String price, int days) throws Exception {
        return createAt("/api/catalogs/Tariffs", Map.of("description", "Тариф", "price", price, "periodDays", days)).path("id").asText();
    }

    private Map<String, Object> line(String tariff, int periods) {
        return Map.of("tariff", tariff, "periods", periods);
    }

    private String path(JsonNode document) {
        return "/api/documents/Subscriptions/" + document.path("id").asText();
    }

    private String topUp(String client, String amount) throws Exception {
        var payment = createAt("/api/documents/Payments", Map.of("client", client, "amount", amount));
        String path = "/api/documents/Payments/" + payment.path("id").asText();
        ok("POST", path + "/post", null);
        return path;
    }

    private void ok(String method, String path, Map<String, Object> body) throws Exception {
        var response = request(method, path, body);
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 204);
    }

    private BigDecimal balance(String client) throws Exception {
        return sum(getJson("/api/registers/AccountBalance/balance?client=" + client), "amount");
    }

    private BigDecimal revenue(String client, String tariff, String resource) throws Exception {
        String path = "/api/registers/TariffRevenue/turnover?from=2000-01-01T00:00:00&to=2100-01-01T00:00:00&client=" + client;
        if (tariff != null) path += "&tariff=" + tariff;
        return sum(getJson(path), resource);
    }

    private BigDecimal sum(JsonNode rows, String field) {
        assertThat(rows.isArray()).as(rows.toString()).isTrue();
        BigDecimal result = BigDecimal.ZERO;
        for (var row : rows) {
            assertThat(row.hasNonNull(field)).as(row.toString()).isTrue();
            result = result.add(row.path(field).decimalValue());
        }
        return result;
    }

    private String statusId(SubscriptionStatus status) {
        return EnumerationPersistence.resolveId(SubscriptionStatus.class, status).toString();
    }
}
