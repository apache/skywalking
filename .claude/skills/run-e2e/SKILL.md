---
name: run-e2e
description: Run SkyWalking E2E tests locally
disable-model-invocation: true
argument-hint: "[test-case-path]"
---

# Run SkyWalking E2E Test

Run an E2E test case using `skywalking-infra-e2e`. The user provides a test case path (e.g., `simple/jdk`, `storage/banyandb`, `alarm`).

## Prerequisites

All tools require **Go** installed. Check `.github/workflows/` for the exact `e2e` commit used in CI.

### e2e CLI

Built from [apache/skywalking-infra-e2e](https://github.com/apache/skywalking-infra-e2e), pinned by commit in CI:

```bash
# Install the pinned commit
go install github.com/apache/skywalking-infra-e2e/cmd/e2e@<commit-id>

# Or clone and build locally (useful when debugging the e2e tool itself)
git clone https://github.com/apache/skywalking-infra-e2e.git
cd skywalking-infra-e2e
git checkout <commit-id>
make build
# binary is in bin/e2e — add to PATH or copy to $GOPATH/bin
```

### swctl, yq, and other tools

E2E test cases run pre-install steps (see `setup.steps` in each `e2e.yaml`) that install tools into `/tmp/skywalking-infra-e2e/bin`. When running locally, you need these tools on your PATH.

**swctl** — SkyWalking CLI, used in verify cases to query OAP's GraphQL API. Pinned at `SW_CTL_COMMIT` in `test/e2e-v2/script/env`:

```bash
# Option 1: Use the install script (same as CI)
bash test/e2e-v2/script/prepare/setup-e2e-shell/install.sh swctl
export PATH=/tmp/skywalking-infra-e2e/bin:$PATH

# Option 2: Build from source
go install github.com/apache/skywalking-cli/cmd/swctl@<SW_CTL_COMMIT>
```

**yq** — YAML processor, used in verify cases:

```bash
# Option 1: Use the install script
bash test/e2e-v2/script/prepare/setup-e2e-shell/install.sh yq
export PATH=/tmp/skywalking-infra-e2e/bin:$PATH

# Option 2: brew install yq (macOS)
```

**Other tools** (only needed for specific test cases):

| Tool | Install script | Used by |
|------|---------------|---------|
| `kubectl` | `install.sh kubectl` | Kubernetes-based tests |
| `helm` | `install.sh helm` | Helm chart tests |
| `istioctl` | `install.sh istioctl` | Istio/service mesh tests |
| `etcdctl` | `install.sh etcdctl` | etcd cluster tests |

All install scripts are at `test/e2e-v2/script/prepare/setup-e2e-shell/`.

## Steps

### 1. Determine the test case

Resolve the user's argument to a full path under `test/e2e-v2/cases/`. If ambiguous, list matching directories and ask.

```bash
ls test/e2e-v2/cases/<argument>/e2e.yaml
```

### 2. Check if rebuild is needed

Compare source file timestamps against the last build:

```bash
# OAP server changes since last build
find oap-server apm-protocol -type f \( \
  -name "*.java" -o -name "*.yaml" -o -name "*.yml" -o \
  -name "*.json" -o -name "*.xml" -o -name "*.properties" -o \
  -name "*.proto" \
\) -newer dist/apache-skywalking-apm-bin.tar.gz 2>/dev/null | head -5

# Test service changes since last build
find test/e2e-v2/java-test-service -type f \( \
  -name "*.java" -o -name "*.xml" -o -name "*.yaml" -o -name "*.yml" \
\) -newer test/e2e-v2/java-test-service/e2e-service-provider/target/*.jar 2>/dev/null | head -5
```

If files are found, warn the user and suggest rebuilding before running.

### 3. Rebuild if needed (only with user confirmation)

```bash
# Rebuild OAP
./mvnw clean flatten:flatten package -Pall -Dmaven.test.skip && make docker

# Rebuild test services
./mvnw -f test/e2e-v2/java-test-service/pom.xml clean flatten:flatten package
```

### 4. Run the E2E test

Set required environment variables and run:

```bash
export SW_AGENT_JDK_VERSION=8
e2e run -c test/e2e-v2/cases/<case-path>/e2e.yaml
```

### 5. If the test fails

Do NOT run cleanup immediately. Instead:

1. Check container logs:
   ```bash
   docker compose -f test/e2e-v2/cases/<case-path>/docker-compose.yml logs oap
   docker compose -f test/e2e-v2/cases/<case-path>/docker-compose.yml logs provider
   ```

2. Run verify separately (can retry after investigation):
   ```bash
   e2e verify -c test/e2e-v2/cases/<case-path>/e2e.yaml
   ```

3. Only cleanup when done debugging:
   ```bash
   e2e cleanup -c test/e2e-v2/cases/<case-path>/e2e.yaml
   ```

### 6. Manually fire each verify query (fast triage)

The `e2e verify` retry loop runs in sequence and stops at the first failing case, so a single bad query hides every case after it. When a verify fails, **run each verify case directly against the still-running OAP** before editing anything — you'll see the real error (bad flag, missing data, wrong expected), not the progress spinner. This is also the right way to author new verify cases: craft the query against live OAP, confirm the actual YAML, then write the expected file.

```bash
# Find the host-side port that infra-e2e bound to OAP's container port 12800.
# (Each run picks a new random port; the trigger log prints it too.)
docker ps --filter "name=skywalking_e2e-oap" --format "{{.Ports}}" \
  | grep -oE "[0-9]+->12800" | head -1
# => e.g. 56381->12800

URL=http://localhost:56381/graphql
SWCTL=/tmp/skywalking-infra-e2e/bin/swctl

# Copy the query from e2e.yaml verbatim, then substitute ${oap_host} → localhost
# and ${oap_12800} → the port you just found:
$SWCTL --display yaml --base-url=$URL service ly IOS
$SWCTL --display yaml --base-url=$URL logs list --service-name=MyiOSApp
$SWCTL --display yaml --base-url=$URL metrics exec --expression=service_cpm --service-name=MyiOSApp
```

When a `swctl` subcommand rejects a flag (`Incorrect Usage: flag provided but not defined: -layer`), the e2e config is using syntax the pinned `swctl` commit doesn't support. Find the right syntax with `swctl <cmd> --help` and update the e2e config. Common cases encountered:

| Broken flag/form | Working form |
|---|---|
| `service ls --layer IOS` | `service ly IOS` |
| `metrics exec ... --is-normal=true` | drop `--is-normal` (default behavior) |

For queries that *don't* use `swctl` (raw `curl` against `/loki/...`, Zipkin, PromQL), hit the matching exposed port:

```bash
curl "http://localhost:$(docker ps --filter name=skywalking_e2e-oap --format '{{.Ports}}' | grep -oE '[0-9]+->3100' | head -1 | cut -d'-' -f1)/loki/api/v1/labels"
```

### 7. UI template changes require a fresh DB

`UITemplateInitializer.initTemplate()` (in `oap-server/server-core`) calls `uiTemplateManagementService.addIfNotExist(setting)` — keyed by the `id` field in each `ui-initialized-templates/**/*.json`. Same ID → skipped. So edits to an *existing* template JSON (adding widgets, relabeling, changing expressions) will **not** be applied on an already-initialized OAP, even after a container restart, because the old copy still lives in storage.

To pick up dashboard JSON changes:

```bash
# Remove both containers — BanyanDB stores state inside the container FS in the
# e2e compose (no named volume), so removing the container wipes state cleanly.
docker rm -f skywalking_e2e-oap-1 skywalking_e2e-banyandb-1

# For compose setups that use a named volume, also:
# docker volume rm <volume-name>

# Then re-run — OAP sees empty storage, loads the new template JSON.
e2e run -c test/e2e-v2/cases/<case>/e2e.yaml
```

Symptom to watch for: you edit the JSON, rebuild, redeploy — dashboard in the UI still shows the pre-edit layout. That's not a caching bug; that's `addIfNotExist` doing exactly what its name says.

### 8. Author the expected YAML from live output

For a new verify case, the workflow is:

1. Fire the query manually (see step 6) and capture the YAML.
2. Pick which fields are *meaningful domain values* (must match exactly) vs *dynamic runtime values* (`notEmpty` / `gt` / `ge`). See `test/e2e-v2/CLAUDE.md` for the decision guide.
3. Write the expected file. If the response is a list, wrap the items in `{{- contains . }} ... {{- end }}` so ordering and extra actual items don't fail the match.
4. Re-run `e2e verify` alone (the containers are still up from the previous run); iterate on the expected file without rebuilding.

### 9. Expected-file authoring traps

These burn CI cycles and pass locally. Each was learned the hard way.

**Unquoted `content: {{ notEmpty .content }}` with `:` inside the value.** Sim-generated or real log content routinely includes colons (`POST https://api.example.com/cart failed: 500`, `HTTP/1.1 500: Internal Server Error`). Without quoting, the template renders to invalid YAML (snakeyaml parses `failed:` as a nested key) and the *whole log entry* marshals to `nil`. Symptom: diff shows `- nil` at *every* position in the expected logs list vs real maps in actual. Fix: wrap in single quotes — `content: '{{ notEmpty .content }}'`. Single-quoted YAML preserves `:` in the scalar; only fails on embedded `'`. Double quotes also work unless the content has `"`.

**Nested `contains` with multiple per-element pattern assertions against a varied stream.** The template renders the block body once per actual element; when the outer block body has *multiple* inner `contains` patterns asserting specific tag key/value pairs, and only *some* actuals satisfy all the inner patterns, `go-cmp` with `contains` can end up comparing `[rendered_for_A0, nil, nil, ...]` vs `[A0, A1, A2, ...]` and fail despite `contains` being permissive on extras. Specifically: outer `contains .logs` with a single log pattern + inner `contains .tags` asserting two distinct key/value pairs. On a simulator emitting heterogeneous errors (js + promise + ajax + pageNotFound), only a subset satisfy the inner assertion. Passes locally with 1–2 logs, fails in CI with 6+.
- Keep the outer `contains` body lenient: field-shape checks (`notEmpty`, `gt`), one discriminator tag that *every* element in the stream carries.
- Cover per-category assertions via separate labeled-metric verify cases, not inside the nested template.
- Rule of thumb: "at least one log exists with the right layer routing" inside the logs expected; per-category coverage via `meter_*_count{label=X}` verify cases.

**Hand-crafted OTLP curl payloads drift from real SDK output.** When the upstream SDK ships a published simulator image (mini-program-monitor's `sim-wechat` / `sim-alipay`, browser-client-js sim, etc.), prefer driving the e2e with that image in `MODE=timed` with a bounded `DURATION_MS` over hand-rolling the OTLP JSON. Hand-crafted payloads miss real-world shape issues: delta-vs-cumulative temporality, label-cardinality surprises, stacktrace formatting variance, attribute key names that changed between SDK versions. Pin to a released tag (`v0.4.0`), not `:latest` or HEAD SHA — reproducibility.

**`timeUnixNano: "0"` in an OTLP metric datapoint.** The receiver propagates this into MAL's bucket computation and the metric lands in the 1970 time bucket — `swctl metrics exec` over the "last N minutes" window won't find it. Either use `$(date +%s)000000000` at setup time or omit the field if the receiver accepts "now" as default.

**Setup-step curl loop with `|| sleep` pattern.** The shell line `for ... do curl && break || sleep 5; done` exits 0 when every attempt connection-refused because the final `sleep 5` returns 0. OAP takes ~50 s to start in CI, so all attempts fail before OAP is ready, and the setup step silently succeeds with zero traffic ingested. Fix: `curl -sS -f --retry 30 --retry-delay 5 --retry-connrefused --retry-all-errors --max-time 10 ...` + `set -e` at step top.

**`swctl` flag rejected.** If a verify case uses a flag the pinned `swctl` commit doesn't support (`service ls --layer` vs `service ly`), the whole case fails 20× before CI gives up. Fire each verify query by hand once before pushing (step 6 above).

**Published image cache miss in CI.** `docker compose pull` sometimes hits rate limits or unreachable registries; the test spins until timeout with "dependency failed to start". Look at the CI log for `Error response from daemon: pull access denied` or `manifest unknown`. If you see that, pin a different image tag that's definitely published (check `docker manifest inspect <tag>` locally), not a floating one.

## Common test cases

| Shorthand | Path |
|-----------|------|
| `simple/jdk` | `test/e2e-v2/cases/simple/jdk/` |
| `storage/banyandb` | `test/e2e-v2/cases/storage/banyandb/` |
| `storage/elasticsearch` | `test/e2e-v2/cases/storage/elasticsearch/` |
| `alarm` | `test/e2e-v2/cases/alarm/` |
| `log` | `test/e2e-v2/cases/log/` |
| `profiling/trace` | `test/e2e-v2/cases/profiling/trace/` |
