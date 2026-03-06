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

## Common test cases

| Shorthand | Path |
|-----------|------|
| `simple/jdk` | `test/e2e-v2/cases/simple/jdk/` |
| `storage/banyandb` | `test/e2e-v2/cases/storage/banyandb/` |
| `storage/elasticsearch` | `test/e2e-v2/cases/storage/elasticsearch/` |
| `alarm` | `test/e2e-v2/cases/alarm/` |
| `log` | `test/e2e-v2/cases/log/` |
| `profiling/trace` | `test/e2e-v2/cases/profiling/trace/` |
