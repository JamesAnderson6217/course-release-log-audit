package education.devtools.http;

import education.devtools.config.InfraiConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class InfraiClient {
    private static final String LOGS_INGEST = "/v1/logs/ingest";
    private static final String LOGS_SEARCH = "/v1/logs/search";
    private static final String ACCOUNT_WHOAMI = "/v1/account/whoami";
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiClient(InfraiConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(config.timeout()).build();
    }

    public Map<String, Object> ingestLogs(List<Map<String, Object>> entries, String idempotencyKey) {
        return call("POST", LOGS_INGEST, Map.of("entries", entries, "idempotency_key", idempotencyKey));
    }

    public Map<String, Object> searchLogs(String query) {
        return call("GET", LOGS_SEARCH + "?q=" + encode(query), null);
    }

    public Map<String, Object> whoAmI() {
        return call("GET", ACCOUNT_WHOAMI, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, Map<String, Object> payload) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                        .timeout(config.timeout())
                        .header("Authorization", "Bearer " + config.apiKey())
                        .header("Accept", "application/json");
                if (payload == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
                else builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(Json.stringify(payload)));

                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Object decoded = Json.parse(response.body()); // The envelope is decoded before status handling.
                if (!(decoded instanceof Map<?, ?> rawEnvelope)) throw new IOException("Expected response envelope");
                Map<String, Object> envelope = (Map<String, Object>) rawEnvelope;
                if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                    Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> rawError
                            ? (Map<String, Object>) rawError : Map.of("message", "Request rejected");
                    String code = String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED"));
                    if (response.statusCode() == 429 && attempt < 3) {
                        pause(response, attempt);
                        continue;
                    }
                    throw new InfraiException(code, response.statusCode(), error);
                }
                if (response.statusCode() >= 500) throw new IOException("Upstream transport status " + response.statusCode());
                Object data = envelope.get("data");
                return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of("value", data);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Infrai request interrupted", exception);
            } catch (IOException exception) {
                throw new IllegalStateException("Infrai transport request failed", exception);
            }
        }
        throw new IllegalStateException("Retry attempts exhausted");
    }

    private static void pause(HttpResponse<?> response, int attempt) throws InterruptedException {
        long fallbackMillis = 250L * (1L << attempt);
        long millis = response.headers().firstValue("Retry-After")
                .map(InfraiClient::retryAfterMillis).orElse(fallbackMillis);
        Thread.sleep(Math.min(millis, Duration.ofSeconds(10).toMillis()));
    }

    private static long retryAfterMillis(String value) {
        try { return Math.max(0, Long.parseLong(value.trim()) * 1000L); }
        catch (NumberFormatException ignored) { return 1000L; }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
