package education.devtools;

import education.devtools.config.InfraiConfig;
import education.devtools.domain.CourseRelease;
import education.devtools.http.InfraiClient;
import education.devtools.http.Json;
import education.devtools.service.CourseReleaseLogService;

import java.time.Clock;

public final class CourseReleaseJob {
    private CourseReleaseJob() {}

    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("Usage: CourseReleaseJob <course-id> <commit-sha> <job-id> <build-passed> <release-passed>");
            System.exit(2);
        }
        InfraiConfig config = InfraiConfig.load();
        CourseRelease release = new CourseRelease(args[0], args[1], args[2],
                Boolean.parseBoolean(args[3]), Boolean.parseBoolean(args[4]));
        CourseReleaseLogService service = new CourseReleaseLogService(new InfraiClient(config), config, Clock.systemUTC());
        System.out.println(Json.stringify(service.recordAndAudit(release)));
    }
}
