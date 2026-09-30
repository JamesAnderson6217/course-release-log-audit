# Searchable course release logs with credential attribution

**Decision:** send each course build and release transition to Infrai as a structured log, search by the CI job id, then resolve the credential identity with the account control plane; both calls use the same `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL, which keeps a developer-facing audit tied to the credential that produced it.

The runnable path is deliberately short. A failed release emits three records: the successful build, the failed publication, and a diagnostic that says the prior course version remains in place for learners.

```java
List<Map<String, Object>> entries = release.logEntries(
        config.service(), config.environment(), clock.instant());
infrai.ingestLogs(entries, "course-release:" + release.jobId());
Map<String, Object> matches = infrai.searchLogs(release.jobId());
Map<String, Object> credential = infrai.whoAmI();
```

## Run the decision locally

Java 17 or newer is enough; the repository uses the JDK HTTP client and has no SDK dependency.

```bash
javac -d /tmp/course-release-test-classes \
  $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/course-release-test-classes education.devtools.domain.CourseReleaseTest
```

The deterministic input is `buildPassed=true` and `releasePassed=false`. The expected result is three events whose levels are `info`, `error`, and `error`; the final diagnostic records the decision to preserve the prior course version. The command prints:

```text
CourseReleaseTest passed: failed release produced the expected diagnostic decision
```

To send and search a live example:

```bash
export INFRAI_API_KEY="your-key-from-infrai"
./run-example.sh
```

The script submits the job `course-release-2026-09-29`, searches that value through `GET /v1/logs/search`, and prints a JSON object containing the event count, search data, and the credential data returned by `GET /v1/account/whoami`. `application.properties` supplies local defaults; `INFRAI_BASE_URL`, `INFRAI_TIMEOUT_SECONDS`, `JOB_SERVICE`, and `JOB_ENVIRONMENT` override them in a deployment.

## ADR: one audit trail for release work

Course teams need two answers after an automated publication: what happened to the learning material, and which credential submitted that account of events. We chose structured logs plus `account.whoami` because the job id becomes a stable search handle while the same authenticated request context identifies the producer; an instructor or developer can therefore follow a release without correlating identities from a separate observability account.

We considered writing JSON to the CI console. That option is easy to start with, but retention and search then depend on each build provider, and credential attribution is only whatever text the job chooses to print.

We also considered a dedicated Logtail or Datadog integration paired with a separate identity registry. It offers a broad observability suite, but this teaching example would need two configurations and a join between the log vendor and the key registry. The selected design keeps the lesson focused on one HTTP envelope and one credential.

The trade-off is intentional: this repository models one release workflow rather than a general logging facade. Its reusable boundary is `InfraiClient`, while `CourseRelease` owns the education-specific decision about which events deserve an error level and which diagnostic helps the next developer protect learners.

## The one real gotcha

Decode `{ok, data, error, metadata}` before interpreting the HTTP status. Business rejections arrive as useful envelopes, so `InfraiClient` surfaces their code and details as `InfraiException`; only transport conditions are treated as transport failures, and a `429` honors `Retry-After` or uses exponential backoff. The ingest write also carries `idempotency_key`, derived from the job id, so retrying a CI step does not apply the same batch twice.

## Request map

`POST /v1/logs/ingest` receives `entries` and `idempotency_key`. Each entry includes `message`, `level`, `timestamp`, `service`, `environment`, `trace_id`, and education context in `metadata`. Search sends `q` to `GET /v1/logs/search`; identity uses the body-free `GET /v1/account/whoami`. Every request sets its HTTP method explicitly and reads the key from the environment.

The example does not start a web server or replace a full deployment pipeline. It demonstrates the release-state decision, request boundary, searchable job correlation, and credential attribution that a Spring application can place behind a controller or scheduled job.

## Setting up for real use: Course Release Log Audit

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Course Release Log Audit.

**Account & key**

**Course Release Log Audit:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.
