package org.example.subscriptions;

import org.example.subscriptions.enums.PaymentMethod;
import su.onno.repository.EnumerationPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentApiTest extends ApiTestSupport {
    @Test
    void savingDoesNotCreditAndPostingCreditsOnlyItsClient() throws Exception {
        String client = client();
        String other = client();
        var payment = createAt("/api/documents/Payments", Map.of("client", client, "amount", "5000.25"));
        String path = "/api/documents/Payments/" + payment.path("id").asText();
        assertThat(payment.path("posted").asBoolean()).isFalse();
        assertThat(payment.path("date").asText()).isNotBlank();
        assertThat(payment.path("methodDisplay").asText()).isEqualTo("Банковская карта");
        assertThat(balance(client)).isZero();
        assertThat(balance(other)).isZero();

        var posted = request("POST", path + "/post", null);
        assertThat(posted.statusCode()).as(posted.body()).isEqualTo(200);
        assertThat(getJson(path).path("posted").asBoolean()).isTrue();
        assertThat(balance(client)).isEqualByComparingTo("5000.25");
        assertThat(balance(other)).isZero();

        // Posted payments are immutable; retry cannot add another receipt.
        var repeated = request("POST", path + "/post", null);
        assertThat(repeated.statusCode()).as(repeated.body()).isEqualTo(400);
        assertThat(balance(client)).isEqualByComparingTo("5000.25");
    }

    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    void acceptsOnlinePaymentMethods(PaymentMethod method) throws Exception {
        String client = client();
        String methodId = EnumerationPersistence.resolveId(PaymentMethod.class, method).toString();
        var payment = createAt("/api/documents/Payments",
                Map.of("client", client, "amount", "100.00", "method", methodId));
        String path = "/api/documents/Payments/" + payment.path("id").asText();
        assertThat(getJson(path).path("method").asText()).isEqualTo(methodId);
        assertThat(getJson(path).path("methodDisplay").asText())
                .isEqualTo(method == PaymentMethod.CARD ? "Банковская карта" : "СБП");
        assertThat(request("POST", path + "/post", null).statusCode()).isEqualTo(200);
        assertThat(balance(client)).isEqualByComparingTo("100.00");
    }

    @Test
    void postedPaymentCannotBeEditedUntilUnposted() throws Exception {
        String client = client();
        var payment = createAt("/api/documents/Payments", Map.of("client", client, "amount", "100.00"));
        String path = "/api/documents/Payments/" + payment.path("id").asText();
        assertThat(request("POST", path + "/post", null).statusCode()).isEqualTo(200);

        var denied = request("PUT", path, Map.of("amount", "200.00"));
        assertThat(denied.statusCode()).as(denied.body()).isEqualTo(400);
        assertThat(getJson(path).path("amount").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(balance(client)).isEqualByComparingTo("100.00");

        var unposted = request("POST", path + "/unpost", null);
        assertThat(unposted.statusCode()).as(unposted.body()).isEqualTo(200);
        assertThat(getJson(path).path("posted").asBoolean()).isFalse();
        assertThat(balance(client)).isZero();

        assertThat(request("PUT", path, Map.of("amount", "200.00")).statusCode()).isEqualTo(200);
        assertThat(request("POST", path + "/post", null).statusCode()).isEqualTo(200);
        assertThat(balance(client)).isEqualByComparingTo("200.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "100.001"})
    void rejectsInvalidAmount(String amount) throws Exception {
        String client = client();
        var response = request("POST", "/api/documents/Payments", Map.of("client", client, "amount", amount));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(balance(client)).isZero();
    }

    @Test
    void rejectsPaymentWithoutClient() throws Exception {
        var response = request("POST", "/api/documents/Payments", Map.of("amount", "100.00"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("client");
    }

    private String client() throws Exception {
        return createAt("/api/catalogs/Clients", Map.of("description", "Клиент для платежа"))
                .path("id").asText();
    }

    private BigDecimal balance(String client) throws Exception {
        var rows = getJson("/api/registers/AccountBalance/balance?client=" + client);
        assertThat(rows.isArray()).as(rows.toString()).isTrue();
        BigDecimal sum = BigDecimal.ZERO;
        for (var row : rows) {
            assertThat(row.hasNonNull("amount")).as(row.toString()).isTrue();
            sum = sum.add(row.path("amount").decimalValue());
        }
        return sum;
    }
}
