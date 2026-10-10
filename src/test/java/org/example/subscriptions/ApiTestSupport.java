package org.example.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:subscriptions-api-test;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
abstract class ApiTestSupport {
    @LocalServerPort
    private int port;

    @Autowired
    protected ObjectMapper mapper;

    private HttpClient http;
    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void signIn() throws Exception {
        http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var login = request("POST", "/api/auth/login", Map.of("username", "admin", "password", "admin"));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        var csrf = request("GET", "/api/auth/csrf", null);
        assertThat(csrf.statusCode()).as(csrf.body()).isEqualTo(200);
        JsonNode token = mapper.readTree(csrf.body());
        csrfHeader = token.path("headerName").asText();
        csrfToken = token.path("token").asText();
        assertThat(csrfToken).isNotBlank();
    }

    protected JsonNode createAt(String path, Map<String, Object> body) throws Exception {
        var response = request("POST", path, body);
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 201);
        JsonNode created = mapper.readTree(response.body());
        assertThat(created.path("id").asText()).isNotBlank();
        return created;
    }

    protected JsonNode getJson(String path) throws Exception {
        var response = request("GET", path, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    protected HttpResponse<String> request(String method, String path, Map<String, Object> body) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        }
        if (csrfToken != null && !method.equals("GET")) {
            builder.header(csrfHeader, csrfToken);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
