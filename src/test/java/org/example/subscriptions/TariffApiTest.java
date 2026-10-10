package org.example.subscriptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TariffApiTest extends ApiTestSupport {
    @Test
    void createsTariffAndPreservesExplicitUnavailabilityOnUpdate() throws Exception {
        var created = createAt("/api/catalogs/Tariffs", Map.of(
                "description", "Месячный", "price", "600.00", "periodDays", 30));
        String path = "/api/catalogs/Tariffs/" + created.path("id").asText();
        var stored = getJson(path);
        assertThat(stored.path("price").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(stored.path("periodDays").asInt()).isEqualTo(30);
        assertThat(stored.path("available").asBoolean()).isTrue();
        var response = request("PUT", path, Map.of("available", false));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        stored = getJson(path);
        assertThat(stored.path("available").asBoolean()).isFalse();
        assertThat(stored.path("price").decimalValue()).isEqualByComparingTo("600.00");
    }

    @Test
    void supportsFreeTariffAndExplicitlyDisabledCreation() throws Exception {
        var tariff = createAt("/api/catalogs/Tariffs", Map.of(
                "description", "Пробный", "price", BigDecimal.ZERO,
                "periodDays", 7, "available", false));
        assertThat(tariff.path("price").decimalValue()).isZero();
        assertThat(tariff.path("available").asBoolean()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"-1.00,30", "10.001,30", "10.00,0", "10.00,-1"})
    void rejectsInvalidPriceOrDuration(String price, int days) throws Exception {
        var response = request("POST", "/api/catalogs/Tariffs", Map.of(
                "description", "Тестовый", "price", price, "periodDays", days));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
    }
}
