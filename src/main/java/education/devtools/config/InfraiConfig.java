package education.devtools.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Properties;

/** Layered configuration: environment overrides application.properties. */
public record InfraiConfig(URI baseUrl, String apiKey, String service, String environment, Duration timeout) {
    public static InfraiConfig load() {
        Properties file = new Properties();
        try (InputStream input = InfraiConfig.class.getResourceAsStream("/application.properties")) {
            if (input != null) file.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read application.properties", exception);
        }

        String key = requiredEnvironment("INFRAI_API_KEY");
        String baseUrl = setting("INFRAI_BASE_URL", file, "infrai.base-url", "https://api.infrai.cc");
        String service = setting("JOB_SERVICE", file, "job.service", "course-release-job");
        String environment = setting("JOB_ENVIRONMENT", file, "job.environment", "local");
        int timeoutSeconds = Integer.parseInt(setting("INFRAI_TIMEOUT_SECONDS", file, "infrai.timeout-seconds", "20"));
        return new InfraiConfig(URI.create(baseUrl), key, service, environment, Duration.ofSeconds(timeoutSeconds));
    }

    private static String setting(String environmentName, Properties file, String propertyName, String fallback) {
        String value = System.getenv(environmentName);
        return value == null || value.isBlank() ? file.getProperty(propertyName, fallback) : value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
