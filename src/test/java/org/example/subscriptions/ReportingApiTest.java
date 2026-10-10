package org.example.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportingApiTest extends ApiTestSupport {
    @Test
    void turnoverGroupsByClientAndTariffAndHonorsPeriodAndUnposting() throws Exception {
        String client = createAt("/api/catalogs/Clients", Map.of("description", "Сводная выручка")).path("id").asText();
        String other = createAt("/api/catalogs/Clients", Map.of("description", "Другой клиент")).path("id").asText();
        String tariff = createAt("/api/catalogs/Tariffs", Map.of("description", "Отчётный тариф", "price", "100", "periodDays", 30)).path("id").asText();
        for (String owner : List.of(client, other)) {
            var payment = createAt("/api/documents/Payments", Map.of("client", owner, "amount", "1000"));
            post("/api/documents/Payments/" + payment.path("id").asText());
        }
        purchase(client, tariff, 1, "2026-03-01T12:00:00");
        String second = purchase(client, tariff, 2, "2026-03-15T12:00:00");
        purchase(client, tariff, 5, "2026-04-01T12:00:00");
        purchase(other, tariff, 4, "2026-03-01T12:00:00");
        String report = "/api/registers/TariffRevenue/turnover?from=2026-03-01T00:00:00&to=2026-03-31T23:59:59&tariff=" + tariff;
        var both = getJson(report);
        assertThat(both.size()).isEqualTo(2);
        assertTotals(getJson(report + "&client=" + client), "300", "3");
        assertTotals(getJson(report + "&client=" + other), "400", "4");

        var unposted = request("POST", second + "/unpost", null);
        assertThat(unposted.statusCode()).as(unposted.body()).isEqualTo(200);
        assertTotals(getJson(report + "&client=" + client), "100", "1");
        assertThat(getJson("/api/registers/AccountBalance/balance?client=" + client).get(0).path("amount").decimalValue())
                .isEqualByComparingTo("400");
    }

    private void assertTotals(JsonNode rows, String amount, String periods) {
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).path("amount").decimalValue()).isEqualByComparingTo(amount);
        assertThat(rows.get(0).path("periods").decimalValue()).isEqualByComparingTo(periods);
    }

    private String purchase(String client, String tariff, int periods, String date) throws Exception {
        var created = createAt("/api/documents/Subscriptions", Map.of("client", client, "date", date,
                "lines", List.of(Map.of("tariff", tariff, "periods", periods))));
        String path = "/api/documents/Subscriptions/" + created.path("id").asText();
        post(path);
        return path;
    }

    private void post(String path) throws Exception {
        var response = request("POST", path + "/post", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }
}
