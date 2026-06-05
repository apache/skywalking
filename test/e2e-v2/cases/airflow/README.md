# Airflow monitoring e2e tests (SWIP-7)

End-to-end tests for [SWIP-7](../../../../docs/en/swip/SWIP-7.md) Airflow monitoring. Two suites
share the same MAL rules but split responsibilities:

| Suite | Entry | Checks | Airflow | CI matrix |
|-------|-------|--------|---------|-----------|
| **Mock (fast, full SWIP-7)** | [`e2e.yaml`](e2e.yaml) | **30** (2 topology + 28 metrics) | OTLP JSON replay via [`mock-sender/`](mock-sender/) | `Airflow` |
| **Real Celery cluster (integration smoke)** | [`e2e-cluster.yaml`](e2e-cluster.yaml) | **26** (2 topology + 24 metrics) | Live Airflow 2.10 CeleryExecutor | `Airflow Cluster` |

Query definitions: [`airflow-cases.yaml`](airflow-cases.yaml) (mock, full matrix) and
[`airflow-cluster-cases.yaml`](airflow-cluster-cases.yaml) (cluster smoke). MAL rules:
[`otel-rules/airflow/`](../../../../oap-server/server-starter/src/main/resources/otel-rules/airflow/).

## Real cluster topology

```
Airflow (scheduler + worker-1 + worker-2 + triggerer)
    │ OTLP HTTP :4318
    ▼
OpenTelemetry Collector
    │ OTLP gRPC
    ▼
SkyWalking OAP (BanyanDB)
    ▼
swctl verify (GraphQL / MQE)
```

| Entity | Value in cluster e2e |
|--------|----------------------|
| Service | `airflow::airflow-e2e-cluster` |
| Instances verified (`host.name`) | `airflow-scheduler`, `airflow-worker-1`, `airflow-triggerer` |
| Compose project | `skywalking_e2e` (see [`scripts/cluster-compose-env.sh`](scripts/cluster-compose-env.sh)) |
| Compose file | [`docker-compose-cluster.yml`](docker-compose-cluster.yml) |

Compose still runs **four** Airflow processes (scheduler, worker-1, worker-2, triggerer) for a
realistic Celery layout. Verify expects **three** OTLP-exporting instances: worker-2 executes
tasks but does not run the e2e Celery sidecar and is not required in the cluster smoke matrix.
The mock suite uses the same three-host OTLP fixture with identical instance metric bindings.

### Workload seeding

[`scripts/seed-e2e-cluster-workload.sh`](scripts/seed-e2e-cluster-workload.sh) triggers DAG runs
3 rounds (default), then waits **240 s** for OTLP export and MAL aggregation:

- Custom: `cluster_smoke`, `cluster_load` ([`cluster/dags/`](cluster/dags/))
- Built-in examples: `example_bash_operator`, `example_python_operator`, `example_branch_operator`,
  `example_short_circuit_operator`
- `LOAD_EXAMPLES=true` loads the full Airflow example DAG set for scheduler activity

Environment overrides: `SEED_ROUNDS`, `SEED_INTERVAL_SECONDS`, `RUN_SECONDS`.

### OTLP metric sources (real cluster)

| Source | Hosts | Instruments |
|--------|-------|-------------|
| **Airflow native OTel** (`AIRFLOW__METRICS__OTEL_ON`) | scheduler, triggerer, workers | Scheduler / executor / pool (scheduler) / heartbeat / orphaned-task / DAG-processing / dataset / triggers (when deferrable + dataset DAGs run) |
| **E2e-only Celery sidecar** [`worker_otel_reporter.py`](cluster/scripts/worker_otel_reporter.py) | `airflow-worker-1` only | `airflow.pool.{open,running,deferred}_slots` — Airflow 2.10 Celery workers do not emit pool gauges natively; values are derived from `celery inspect active`, not Airflow Stats |

Dedicated e2e DAGs (native OTel, no reporter):

| DAG | Purpose |
|-----|---------|
| [`e2e_deferrable.py`](cluster/dags/e2e_deferrable.py) | `TimeDeltaSensorAsync` → triggerer exports `triggers_*` counters |
| [`e2e_dataset.py`](cluster/dags/e2e_dataset.py) | `e2e_dataset_producer` / `e2e_dataset_consumer` — scheduler `asset_orphaned` / `asset_triggered_dagruns` in cluster smoke (`asset_updates` is mock-only) |

`meter_airflow_instance_asset_orphaned` is verified in the cluster smoke when Airflow emits the
gauge (0 when no orphans). Metrics that need synthetic OTLP or rare failure events are **mock-only**
(see [Coverage split](#coverage-split) below).

## Coverage split

| Concern | Mock (`airflow-cases.yaml`) | Cluster (`airflow-cluster-cases.yaml`) |
|---------|----------------------------|----------------------------------------|
| **Goal** | Full SWIP-7 MAL/MQE contract | Real Airflow → OTel → OAP integration |
| **Topology** | 3 instances | 3 OTLP-exporting instances |
| **Service metrics** | 12 | 11 (excludes `asset_updates`) |
| **Instance metrics** | 16 | 13 (excludes `asset_updates`, `triggers_failed`, `triggers_blocked_main_thread`) |
| **Total checks** | **30** | **26** |

**Mock-only metrics** (synthetic OTLP in [`mock-data/otel-airflow-metrics.json`](mock-data/otel-airflow-metrics.json)):

| Metric | Reason cluster omits it |
|--------|-------------------------|
| `meter_airflow_asset_updates` (service + instance) | Dataset producer timing is hard to stabilize; mock sender injects `airflow.dataset.updates` |
| `meter_airflow_instance_triggers_failed` | Airflow may not export the counter when no triggers fail (`null`, not `0`) |
| `meter_airflow_instance_triggers_blocked_main_thread` | Same — absent unless a trigger blocks the main thread |

Cluster still verifies `triggers_succeeded` on the triggerer (deferrable DAG smoke) plus
`asset_orphaned` / `asset_triggered_dagruns` when the scheduler emits them.

## Test coverage matrix (full SWIP-7 — mock suite)

Each row is one `swctl metrics exec` assertion. Expected template:
[`expected/metrics-has-value.yml`](expected/metrics-has-value.yml) (non-null numeric time series;
`0` is valid). The cluster smoke uses the same expressions except for the mock-only rows below.

### Topology (2 checks)

| # | Query | Expected |
|---|-------|----------|
| 1 | `swctl service ly AIRFLOW` | Service `airflow::airflow-e2e-cluster`, layer `AIRFLOW` — [`expected/service-cluster.yml`](expected/service-cluster.yml) |
| 2 | `swctl instance ls --service-name=airflow::airflow-e2e-cluster` | 3 instances — [`expected/instance-cluster.yml`](expected/instance-cluster.yml) |

Mock suite uses `airflow::airflow-cluster` — [`expected/service.yml`](expected/service.yml),
[`expected/instance.yml`](expected/instance.yml) (same 3 hosts). Cluster verify matches mock
instance bindings; worker-2 remains in compose for Celery realism but is not an e2e assertion.

### Service metrics (12)

| # | MQE expression | Airflow OTel instrument (MAL) | Cluster smoke |
|---|----------------|----------------------------------|---------------|
| 1 | `meter_airflow_scheduler_tasks_executable` | `airflow.scheduler.tasks_executable` | yes |
| 2 | `meter_airflow_executor_queued_tasks` | `airflow.executor.queued_tasks` | yes |
| 3 | `meter_airflow_executor_running_tasks` | `airflow.executor.running_tasks` | yes |
| 4 | `meter_airflow_executor_open_slots` | `airflow.executor.open_slots` | yes |
| 5 | `meter_airflow_pool_queued_slots` | `airflow.pool.queued_slots` | yes |
| 6 | `meter_airflow_pool_deferred_slots` | `airflow.pool.deferred_slots` | yes |
| 7 | `meter_airflow_pool_scheduled_slots` | `airflow.pool.scheduled_slots` | yes |
| 8 | `meter_airflow_scheduler_heartbeat` | `airflow.scheduler.heartbeat` | yes |
| 9 | `meter_airflow_scheduler_orphaned_tasks_cleared` | `airflow.scheduler.orphaned_tasks_cleared` | yes |
| 10 | `meter_airflow_scheduler_orphaned_tasks_adopted` | `airflow.scheduler.orphaned_tasks_adopted` | yes |
| 11 | `meter_airflow_dag_file_queue_size` | `airflow.dag_processing.file_path_queue_size` | yes |
| 12 | `meter_airflow_asset_updates` | `airflow.dataset.updates` | mock only |

### Instance metrics (16)

Instance-scoped queries use `--instance-name={host.name}`.

| # | MQE expression | Scoped instance | Cluster smoke |
|---|----------------|-----------------|---------------|
| 1 | `meter_airflow_instance_pool_open_slots` | `airflow-worker-1` | yes (e2e Celery sidecar) |
| 2 | `meter_airflow_instance_pool_deferred_slots` | `airflow-worker-1` | yes (e2e Celery sidecar) |
| 3 | `meter_airflow_instance_pool_running_slots` | `airflow-worker-1` | yes (e2e Celery sidecar) |
| 4 | `meter_airflow_instance_pool_scheduled_slots` | `airflow-scheduler` | yes |
| 5 | `meter_airflow_instance_triggerer_heartbeat` | `airflow-triggerer` | yes |
| 6 | `meter_airflow_instance_triggers_blocked_main_thread` | `airflow-triggerer` | mock only |
| 7 | `meter_airflow_instance_triggers_failed` | `airflow-triggerer` | mock only |
| 8 | `meter_airflow_instance_triggers_succeeded` | `airflow-triggerer` | yes |
| 9 | `meter_airflow_instance_scheduler_tasks_executable` | `airflow-scheduler` | yes |
| 10 | `meter_airflow_instance_scheduler_orphaned_tasks_cleared` | `airflow-scheduler` | yes |
| 11 | `meter_airflow_instance_scheduler_orphaned_tasks_adopted` | `airflow-scheduler` | yes |
| 12 | `meter_airflow_instance_executor_queued_tasks` | `airflow-scheduler` | yes |
| 13 | `meter_airflow_instance_executor_running_tasks` | `airflow-scheduler` | yes |
| 14 | `meter_airflow_instance_asset_updates` | `airflow-worker-1` | mock only |
| 15 | `meter_airflow_instance_asset_orphaned` | `airflow-scheduler` | yes |
| 16 | `meter_airflow_instance_asset_triggered_dagruns` | `airflow-scheduler` | yes |

**Mock total: 30 checks** (2 topology + 28 metrics) = full SWIP-7 panel set.
**Cluster total: 26 checks** (2 topology + 24 metrics) = integration smoke.

Both suites use the same instance bindings for triggerer (`airflow-triggerer`), dataset
orphan/triggered DagRuns (`airflow-scheduler`), and worker pool gauges (`airflow-worker-1`).

## Running locally

### Prerequisites

- Docker / Docker Compose
- Git Bash on Windows (use `/usr/bin/bash`, not MSYS-only shell)
- Go (for `swctl` install via setup scripts)

### Real cluster — one command

```bash
cd /path/to/skywalking
export OTEL_COLLECTOR_VERSION=0.102.1 SW_AGENT_JDK_VERSION=8
chmod +x test/e2e-v2/cases/airflow/scripts/*.sh
/usr/bin/bash test/e2e-v2/cases/airflow/scripts/run-full-cluster-e2e.sh
```

Steps inside: `compose up` → install tools → wait for scheduler → seed workload → verify.

### Real cluster — incremental

```bash
set -a && source test/e2e-v2/script/env && set +a
source test/e2e-v2/cases/airflow/scripts/cluster-compose-env.sh
dc up -d
/usr/bin/bash test/e2e-v2/cases/airflow/scripts/run-cluster-setup.sh
/usr/bin/bash test/e2e-v2/cases/airflow/scripts/verify-cluster-e2e.sh
```

### Mock suite (infra-e2e)

```bash
# From repo root, with e2e CLI installed
e2e run -c test/e2e-v2/cases/airflow/e2e.yaml
```

### Verify tuning

| Variable | Default | Purpose |
|----------|---------|---------|
| `VERIFY_RETRIES` | `18` | Poll attempts per check |
| `VERIFY_INTERVAL_SECONDS` | `10` | Sleep between attempts |
| `VERIFY_REPORT` | `test/e2e-v2/cases/airflow/cluster-e2e-report.txt` | Report output path |

### Windows notes

- Ensure `test/e2e-v2/script/env` uses **LF** line endings (CRLF breaks BanyanDB image tags).
- OAP port is **dynamic** (`12800` without host binding in compose); scripts resolve it via
  `docker compose port`.
- Use `run-full-cluster-e2e.sh` + `verify-cluster-e2e.sh` instead of raw `e2e run` verify on
  Windows (`${oap_12800}` substitution issue in infra-e2e).
- Setup steps do not persist `PATH` between infra-e2e steps — `run-cluster-setup.sh` merges
  tool install, health wait, and workload seed into one script.

## Verification report

Each cluster verify run writes a line-oriented report to
`cluster-e2e-report.txt` (overwritten, gitignored). Full compose logs from local runs may be
captured in `cluster-e2e-run.log` (gitignored).

### Report format

```
=== Airflow cluster e2e verify (integration smoke) ===
time: <UTC ISO8601>
compose project: skywalking_e2e
OAP GraphQL: http://localhost:<port>/graphql

  PASS|FAIL: <check description>
        detail: <on failure only>

=== Summary ===
PASS: <n>  FAIL: <n>  TOTAL: 26
Report: test/e2e-v2/cases/airflow/cluster-e2e-report.txt
```

Pass criteria per metric: `swctl metrics exec` returns `TIME_SERIES_VALUES` with at least one
point whose `value` is a non-null number (zero counts as pass).

### Cluster smoke checklist (26 checks)

**Topology (2)**

- service ly AIRFLOW → `airflow::airflow-e2e-cluster`
- instances: scheduler, worker-1, triggerer

**Service metrics (11)** — all except `meter_airflow_asset_updates`

**Instance metrics (13)** — excludes `asset_updates`, `triggers_failed`, `triggers_blocked_main_thread`

Full SWIP-7 (30 checks) baseline is the mock suite — see `mock-e2e-report.txt` (gitignored).

<details>
<summary>Historical full cluster run (30 checks, superseded by split above)</summary>

2026-06-02 run achieved 30/30 before the mock/cluster split; several checks were flaky on real
Airflow without synthetic OTLP. Current cluster scope is the 26-check integration smoke.

</details>

## File reference

| Path | Role |
|------|------|
| [`e2e.yaml`](e2e.yaml) | Mock suite entry (CI `Airflow`) |
| [`e2e-cluster.yaml`](e2e-cluster.yaml) | Real cluster entry (CI `Airflow Cluster`, timeout 35m) |
| [`docker-compose.yml`](docker-compose.yml) | Mock stack (OAP + mock sender) |
| [`mock-sender/`](mock-sender/) | Case-local OTLP JSON replay sender (supports `increase('PT1M')` metrics) |
| [`docker-compose-cluster.yml`](docker-compose-cluster.yml) | Real Airflow Celery stack |
| [`otel-collector-config.yaml`](otel-collector-config.yaml) | Collector → OAP pipeline |
| [`mock-data/otel-airflow-metrics.json`](mock-data/otel-airflow-metrics.json) | Mock OTLP payload |
| [`scripts/run-full-cluster-e2e.sh`](scripts/run-full-cluster-e2e.sh) | Local end-to-end driver |
| [`scripts/run-cluster-setup.sh`](scripts/run-cluster-setup.sh) | Tools + health + workload |
| [`scripts/verify-cluster-e2e.sh`](scripts/verify-cluster-e2e.sh) | Cluster integration smoke (26 swctl checks) |
| [`scripts/wait-scheduler-healthy.sh`](scripts/wait-scheduler-healthy.sh) | Scheduler health gate |
| `cluster-e2e-report.txt` | Generated verify report (gitignored) |

## CI

GitHub Actions matrix (`.github/workflows/skywalking.yaml`):

- **Airflow** — `test/e2e-v2/cases/airflow/e2e.yaml`
- **Airflow Cluster** — `test/e2e-v2/cases/airflow/e2e-cluster.yaml`

Cluster job uses infra-e2e `verify` with [`airflow-cluster-cases.yaml`](airflow-cluster-cases.yaml)
(Linux; `${oap_host}:${oap_12800}` substitution works). Local Windows runs use
[`verify-cluster-e2e.sh`](scripts/verify-cluster-e2e.sh) instead.

## Related docs

- [Airflow monitoring setup](../../../../docs/en/setup/backend/backend-airflow-monitoring.md)
- [SWIP-7 proposal](../../../../docs/en/swip/SWIP-7.md)
- [E2E test guide](../../CLAUDE.md)
