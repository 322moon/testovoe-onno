package org.example.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClientApiTest extends ApiTestSupport {
    @Test
    void createsAndReadsClientWithDefaultsThroughGeneratedApi() throws Exception {
        LocalDate before = LocalDate.now();
        JsonNode created = create(Map.of(
                "description", "Анна Иванова",
                "email", "anna@example.com",
                "phone", "+7 900 123-45-67"));
        var response = request("GET", "/api/catalogs/Clients/" + created.path("id").asText(), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode stored = mapper.readTree(response.body());
        assertThat(stored.path("description").asText()).isEqualTo("Анна Иванова");
        assertThat(stored.path("email").asText()).isEqualTo("anna@example.com");
        assertThat(stored.path("phone").asText()).isEqualTo("+7 900 123-45-67");
        assertThat(stored.path("code").asText()).startsWith("CL-");
        assertThat(stored.path("statusDisplay").asText()).isEqualTo("Активен");
        assertThat(LocalDate.parse(stored.path("registrationDate").asText()))
                .isBetween(before, LocalDate.now());
    }

    @Test
    void acceptsOptionalContactsAndPreservesRegistrationDateOnPartialUpdate() throws Exception {
        JsonNode created = create(Map.of("description", "Первое имя", "registrationDate", "2020-01-15"));
        String path = "/api/catalogs/Clients/" + created.path("id").asText();
        var updated = request("PUT", path, Map.of("description", "Новое имя"));
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
        var response = request("GET", path, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode stored = mapper.readTree(response.body());
        assertThat(stored.path("description").asText()).isEqualTo("Новое имя");
        assertThat(stored.path("registrationDate").asText()).isEqualTo("2020-01-15");
        assertThat(stored.path("statusDisplay").asText()).isEqualTo("Активен");
    }

    @Test
    void rejectsInvalidEmailThroughApi() throws Exception {
        var response = request("POST", "/api/catalogs/Clients",
                Map.of("description", "Клиент", "email", "invalid-email"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("email");
    }

    @Test
    void rejectsBlankNameThroughApi() throws Exception {
        var response = request("POST", "/api/catalogs/Clients", Map.of("description", "   "));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("Укажите имя или название клиента");
    }

    @Test
    void requiresAuthenticationForClientList() throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/list/catalogs/Clients"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    private JsonNode create(Map<String, Object> body) throws Exception {
        var response = request("POST", "/api/catalogs/Clients", body);
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 201);
        JsonNode created = mapper.readTree(response.body());
        assertThat(created.path("id").asText()).isNotBlank();
        return created;
    }

}
