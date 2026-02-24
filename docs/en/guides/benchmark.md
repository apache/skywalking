# Benchmark Tests

The `benchmarks/` directory provides a framework for running system-level benchmarks against
SkyWalking deployments. It separates **environment setup** from **benchmark cases** so that the
same case can run against different topologies, and the same environment can serve multiple cases.

## Directory Structure

```
benchmarks/
├── run.sh                          # Entry point (two modes)
├── env                             # Image repos and versions (local/remote)
├── envs-setup/                     # Reusable environment definitions
│   └── <env-name>/
│       ├── setup.sh                # Boots the environment, writes env-context.sh
│       └── ...                     # K8s manifests, Helm values, etc.
├── cases/                          # Reusable benchmark cases
│   └── <case-name>/
│       └── run.sh                  # Reads env-context.sh, runs the benchmark
└── reports/                        # Git-ignored, timestamped output
```

## Quick Start

### Prerequisites

- Docker
- [Kind](https://kind.sigs.k8s.io/) (version checked automatically against the K8s node image)
- kubectl (version skew checked against the K8s node — max ±1 minor)
- [Helm](https://helm.sh/) >= 3.12.0
- [istioctl](https://istio.io/latest/docs/setup/getting-started/#download) (for Istio-based environments)
- [swctl](https://github.com/apache/skywalking-cli) (SkyWalking CLI)
- Pre-built OAP and UI Docker images

```bash
# Build OAP/UI images (from repo root)
./mvnw clean package -Pall -Dmaven.test.skip
make docker
```

### Two Modes

**Mode 1 — Setup environment only.** Useful for manual inspection or running cases later.

```bash
./benchmarks/run.sh setup <env-name>
```

The script boots the environment (Kind cluster, Helm deploy, traffic generator, port-forwards)
and prints the path to the generated `env-context.sh` file. You can then attach any case to it:

```bash
./benchmarks/cases/<case-name>/run.sh <path-to-env-context.sh>
```

**Mode 2 — Setup + run case end-to-end.**

```bash
./benchmarks/run.sh run <env-name> <case-name>
```

This chains environment setup and benchmark execution in one command.

### Example

```bash
# Full run: cluster OAP + BanyanDB environment, thread analysis case
./benchmarks/run.sh run istio-cluster_oap-banyandb thread-analysis
```

## Available Environments

| Name | Description |
|------|-------------|
| `istio-cluster_oap-banyandb` | 2-node OAP cluster with BanyanDB on Kind. Istio ALS (Access Log Service) for telemetry. Bookinfo sample app with Envoy sidecars at ~5 RPS. Deployed via SkyWalking Helm chart. |

### Environment Pre-checks

Each environment setup script validates before proceeding:

- Required CLI tools are installed (`kind`, `kubectl`, `helm`, `docker`)
- **Kind version** meets the minimum for the K8s node image (compatibility table in the script)
- **kubectl version skew** is within ±1 minor of the K8s node version
- **Helm version** meets the minimum
- **Docker resources** (CPUs, memory) are reported and warned if below recommended thresholds

## Available Cases

| Name | Description |
|------|-------------|
| `thread-analysis` | Collects periodic OAP thread dumps, monitors metrics via swctl, and produces a thread pool analysis report with per-pool state breakdown and count trends. |

### thread-analysis

Configurable via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DUMP_COUNT` | 5 | Number of thread dump rounds |
| `DUMP_INTERVAL` | 60 | Seconds between dump rounds |

## Reports

Reports are saved under `benchmarks/reports/` (git-ignored) with a timestamped directory:

```
benchmarks/reports/<env-name>/<case-name>/<YYYYMMDD-HHMMSS>/
├── environment.txt      # Host, Docker, K8s resources, tool versions
├── node-resources.txt   # K8s node capacity and allocatable
├── thread-analysis.txt  # Thread pool summary and trend table
├── metrics-round-*.yaml # Periodic swctl query results
├── oap-*-dump-*.txt    # Raw jstack thread dumps
└── env-context.sh       # Environment variables for the case
```

## Adding a New Environment

1. Create `benchmarks/envs-setup/<env-name>/` with a `setup.sh`.
2. `setup.sh` must read `$REPORT_DIR` (set by the caller) and write `$REPORT_DIR/env-context.sh`
   exporting at least:
   - `ENV_NAME`, `NAMESPACE`, `CLUSTER_NAME`
   - `OAP_HOST`, `OAP_PORT`, `OAP_SELECTOR`
   - `REPORT_DIR`
   - Resource info (`DOCKER_CPUS`, `DOCKER_MEM_GB`, tool versions, etc.)
3. Add the environment to the table above.

## Adding a New Case

1. Create `benchmarks/cases/<case-name>/` with a `run.sh`.
2. `run.sh` takes the env-context file as its first argument and sources it.
3. The case uses `$NAMESPACE`, `$OAP_HOST`, `$OAP_PORT`, `$OAP_SELECTOR`, `$REPORT_DIR`, etc.
   from the context.
4. Add the case to the table above.

## Cleanup

**`run` mode** automatically deletes the Kind cluster and prunes Docker resources when the
benchmark finishes (whether it succeeds or fails).

**`setup` mode** leaves the environment running for manual inspection. If setup fails, the
cluster and Docker resources are cleaned up automatically. To tear down a successfully set up
environment manually:

```bash
kind delete cluster --name benchmark-cluster
```
