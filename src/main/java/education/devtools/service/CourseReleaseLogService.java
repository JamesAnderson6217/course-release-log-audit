package education.devtools.service;

import education.devtools.config.InfraiConfig;
import education.devtools.domain.CourseRelease;
import education.devtools.http.InfraiClient;

import java.time.Clock;
import java.util.List;
import java.util.Map;

public final class CourseReleaseLogService {
    private final InfraiClient infrai;
    private final InfraiConfig config;
    private final Clock clock;

    public CourseReleaseLogService(InfraiClient infrai, InfraiConfig config, Clock clock) {
        this.infrai = infrai;
        this.config = config;
        this.clock = clock;
    }

    public Map<String, Object> recordAndAudit(CourseRelease release) {
        List<Map<String, Object>> entries = release.logEntries(config.service(), config.environment(), clock.instant());
        infrai.ingestLogs(entries, "course-release:" + release.jobId());
        Map<String, Object> matches = infrai.searchLogs(release.jobId());
        Map<String, Object> credential = infrai.whoAmI();
        return Map.of("job_id", release.jobId(), "events", entries.size(), "matches", matches, "credential", credential);
    }
}
