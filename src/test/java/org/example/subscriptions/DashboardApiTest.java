package org.example.subscriptions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardApiTest extends ApiTestSupport {
    @Test
    void dashboardAndNavigationExposePeriodWidgetsChartsAndRecentDocuments() throws Exception {
        String page = getJson("/api/divkit/dashboard?viewport=desktop").toString();
        assertThat(page).contains("Дашборд", "timeRange", "Поступления", "Выручка", "Подключённые периоды",
                "Поступления по датам", "Выручка по тарифам", "Последние платежи", "Последние подписки",
                "tariff_display", "RUB");
        assertThat(getJson("/api/divkit/shell?viewport=desktop").toString()).contains("dashboard", "Дашборд");
    }

    @Test
    void dashboardDataHonorsPeriodPostingAndCancellation() throws Exception {
        String client = createAt("/api/catalogs/Clients", Map.of("description", "Дашборд")).path("id").asText();
        String tariff = createAt("/api/catalogs/Tariffs", Map.of("description", "Тариф дашборда", "price", "100", "periodDays", 30)).path("id").asText();
        var march = createAt("/api/documents/Payments", Map.of("client", client, "amount", "500", "date", "2026-03-15T12:00:00"));
        post("Payments", march.path("id").asText());
        var april = createAt("/api/documents/Payments", Map.of("client", client, "amount", "700", "date", "2026-04-15T12:00:00"));
        post("Payments", april.path("id").asText());
        createAt("/api/documents/Payments", Map.of("client", client, "amount", "999", "date", "2026-03-15T12:00:00"));
        var subscription = createAt("/api/documents/Subscriptions", Map.of("client", client, "date", "2026-03-15T12:00:00",
                "lines", List.of(Map.of("tariff", tariff, "periods", 2))));
        post("Subscriptions", subscription.path("id").asText());
        String aggregate = "/api/list/documents/Payments/aggregate?metric=sum&field=amount&filter=_posted%20=%20true%20AND%20client%20=%20'" + client + "'&dateField=date";
        String marchPeriod = "&from=2026-03-01T00:00:00&to=2026-03-31T23:59:59";
        assertThat(getJson(aggregate + marchPeriod).path("buckets").get(0).path("value").decimalValue()).isEqualByComparingTo("500");
        assertThat(getJson(aggregate + "&from=2026-04-01T00:00:00&to=2026-04-30T23:59:59").path("buckets").get(0).path("value").decimalValue()).isEqualByComparingTo("700");
        String turnover = "/api/registers/TariffRevenue/turnover?from=2026-03-01T00:00:00&to=2026-03-31T23:59:59&client=" + client;
        var rows = getJson(turnover);
        assertThat(rows.get(0).path("amount").decimalValue()).isEqualByComparingTo("200");
        assertThat(rows.get(0).path("periods").decimalValue()).isEqualByComparingTo("2");
        assertThat(rows.get(0).path("tariff_display").asText()).isEqualTo("Тариф дашборда");
        var cancelled = request("POST", "/api/actions/documents/Subscriptions/cancel?id=" + subscription.path("id").asText(),
                Map.of("inputs", Map.of("reason", "Проверка дашборда")));
        assertThat(cancelled.statusCode()).as(cancelled.body()).isEqualTo(200);
        assertThat(getJson(turnover)).isEmpty();
        assertThat(getJson(aggregate + marchPeriod).path("buckets").get(0).path("value").decimalValue()).isEqualByComparingTo("500");
    }

    private void post(String type, String id) throws Exception {
        var response = request("POST", "/api/documents/" + type + "/" + id + "/post", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }
}
