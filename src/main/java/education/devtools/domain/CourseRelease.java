package education.devtools.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CourseRelease(String courseId, String commitSha, String jobId, boolean buildPassed, boolean releasePassed) {
    public CourseRelease {
        if (courseId.isBlank() || commitSha.isBlank() || jobId.isBlank()) {
            throw new IllegalArgumentException("courseId, commitSha, and jobId are required");
        }
    }

    public List<Map<String, Object>> logEntries(String service, String environment, Instant timestamp) {
        Map<String, Object> shared = Map.of(
                "course_id", courseId,
                "commit_sha", commitSha,
                "job_id", jobId
        );
        Map<String, Object> build = entry(buildPassed ? "info" : "error",
                "course build " + (buildPassed ? "passed" : "failed"), service, environment, timestamp, shared);
        if (!buildPassed) {
            return List.of(build, entry("error", "developer diagnostic: build blocked release",
                    service, environment, timestamp, shared));
        }
        Map<String, Object> release = entry(releasePassed ? "info" : "error",
                "course release " + (releasePassed ? "published" : "failed"), service, environment, timestamp, shared);
        if (releasePassed) return List.of(build, release);
        return List.of(build, release, entry("error", "developer diagnostic: preserve prior course version",
                service, environment, timestamp, shared));
    }

    private static Map<String, Object> entry(String level, String message, String service,
                                              String environment, Instant timestamp, Map<String, Object> metadata) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("message", message);
        entry.put("level", level);
        entry.put("timestamp", timestamp.toString());
        entry.put("service", service);
        entry.put("environment", environment);
        entry.put("trace_id", String.valueOf(metadata.get("job_id")));
        entry.put("metadata", metadata);
        return entry;
    }
}
