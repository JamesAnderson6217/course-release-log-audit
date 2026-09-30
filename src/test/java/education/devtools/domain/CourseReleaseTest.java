package education.devtools.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CourseReleaseTest {
    private CourseReleaseTest() {}

    public static void main(String[] args) {
        CourseRelease failedRelease = new CourseRelease("algebra-101", "a18c0de", "job-42", true, false);
        List<Map<String, Object>> entries = failedRelease.logEntries(
                "course-release-job", "test", Instant.parse("2026-09-29T08:00:00Z"));

        check(entries.size() == 3, "a failed release must emit build, release, and diagnostic events");
        check("info".equals(entries.get(0).get("level")), "the successful build is informational");
        check("error".equals(entries.get(1).get("level")), "the failed release is an error");
        check(entries.get(2).get("message").toString().contains("preserve prior course version"),
                "the diagnostic must state the learner-facing safety decision");
        check("job-42".equals(entries.get(2).get("trace_id")), "all events remain searchable by job id");
        System.out.println("CourseReleaseTest passed: failed release produced the expected diagnostic decision");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
