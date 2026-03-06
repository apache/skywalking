---
name: test
description: Run unit tests, integration tests, or slow integration tests matching CI. Use to validate changes before submitting a PR.
argument-hint: "[unit|integration|slow|module-name]"
---

# Tests

Run tests matching CI configuration.

## Commands by argument

### `unit` or no argument — unit tests

```bash
./mvnw clean test -q -B -D"checkstyle.skip"
```

CI runs this on:
- JDK 11: ubuntu, macOS, Windows
- JDK 17, 21, 25: ubuntu only

### `integration` — integration tests (excludes slow)

```bash
./mvnw -B clean integration-test -Dcheckstyle.skip -DskipUTs=true -DexcludedGroups=slow
```

CI runs this on JDK 11, 17, 21, 25 (ubuntu only).

### `slow` — slow integration tests

```bash
./mvnw -B clean integration-test -Dcheckstyle.skip -DskipUTs=true \
  -Dit.test=org.apache.skywalking.library.elasticsearch.ElasticSearchIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
```

CI runs on JDK 11 (ubuntu only). Currently only ElasticSearch/OpenSearch IT is in the slow matrix.

### Module name — single module tests

```bash
# Unit tests for a specific module
./mvnw clean test -pl oap-server/analyzer/<module-name> -D"checkstyle.skip"

# Integration tests for a specific module
./mvnw -B clean integration-test -pl oap-server/analyzer/<module-name> -Dcheckstyle.skip -DskipUTs=true
```

## Test frameworks

- JUnit 5 (`org.junit.jupiter`)
- Mockito for mocking
- AssertJ for assertions
- PowerMock for reflection utilities

## Test naming conventions

| Type | Pattern | Maven phase |
|------|---------|-------------|
| Unit tests | `*Test.java` | `test` |
| Integration tests | `IT*.java` or `*IT.java` | `integration-test` |

## Slow test tagging

Tests tagged with `@Tag("slow")` are excluded from the normal integration-test job and run separately in the slow-integration-test job. Use this tag for tests that spin up external services (Elasticsearch, etc.) and take significant time.

## CI retry behavior

All three CI jobs retry on failure (run the same command twice with `||`). This handles flaky tests but masks intermittent issues — if a test fails locally, investigate rather than relying on retries.

## CI reference

CI workflow: `.github/workflows/skywalking.yaml`

| Job | JDK | OS | Timeout |
|-----|-----|----|---------|
| `unit-test` | 11 (3 OS) + 17, 21, 25 (ubuntu) | ubuntu, macOS, Windows | 30 min |
| `integration-test` | 11, 17, 21, 25 | ubuntu | 60 min |
| `slow-integration-test` | 11 | ubuntu | 60 min |
