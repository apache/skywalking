# Airflow monitoring e2e tests (SWIP-7)

End-to-end tests for [SWIP-7](../../../../docs/en/swip/SWIP-7.md).

```
airflow/
├── mock/     # CI "Airflow" — OTLP JSON replay (29 checks)
├── cluster/  # CI "Airflow Cluster" — real Celery + native OTel (16 checks)
└── README.md
```

MAL rules: [`otel-rules/airflow/`](../../../../oap-server/server-starter/src/main/resources/otel-rules/airflow/)
(**27** metrics: 11 Service + 16 Instance).

## Run locally (same as CI)

From the repository root, with [skywalking-infra-e2e](https://github.com/apache/skywalking-infra-e2e)
and [swctl](https://github.com/apache/skywalking-cli) installed:

```bash
# Mock — full 27-metric SWIP-7 contract via OTLP JSON replay
e2e run -c test/e2e-v2/cases/airflow/mock/e2e.yaml

# Cluster — native scheduler/triggerer OTel on a real Celery stack
e2e run -c test/e2e-v2/cases/airflow/cluster/e2e.yaml
```

CI builds OAP with `make docker.all` then runs the same configs via
[`.github/workflows/skywalking.yaml`](../../../../.github/workflows/skywalking.yaml)
(matrix entries **Airflow** and **Airflow Cluster**).

Each suite runs **2 entity checks** (Service on `AIRFLOW` layer + scheduler/triggerer instances)
then **metric checks** via `metrics exec`.

## Mock suite (`mock/`)

Replays synthetic OTLP JSON — full **27**-metric SWIP-7 contract.

| File | Role |
|------|------|
| [`e2e.yaml`](mock/e2e.yaml) | CI entry |
| [`airflow-cases.yaml`](mock/airflow-cases.yaml) | 29 swctl checks |
| [`docker-compose.yml`](mock/docker-compose.yml) | OAP + BanyanDB + OTLP replay sidecar |
| [`mock-data/otel-airflow-metrics.json`](mock/mock-data/otel-airflow-metrics.json) | Payload |

## Cluster suite (`cluster/`)

Real Airflow Celery cluster with native scheduler/triggerer OTel export — **14** native metrics
(flake-prone gauges omitted; full matrix stays in mock).

| File | Role |
|------|------|
| [`e2e.yaml`](cluster/e2e.yaml) | CI entry (timeout 50m, `fail-fast: false`) |
| [`airflow-cases.yaml`](cluster/airflow-cases.yaml) | 16 swctl checks |
| [`docker-compose.yml`](cluster/docker-compose.yml) | Airflow + Collector + OAP |
| [`dags/`](cluster/dags/) | Workload DAGs |
| [`setup.sh`](cluster/setup.sh) | Health wait + [`seed-workload.sh`](cluster/seed-workload.sh) |

`seed-workload.sh` drives asset, deferrable, and load DAGs, then holds for OTel export.
Optional env: `ASSET_ROUNDS`, `DEFERRABLE_TRIGGERS`, `LOAD_BURSTS`, `HOLD_SECONDS`, `OTEL_FLUSH_SECONDS`.

## Coverage split

| | Mock | Cluster |
|---|------|---------|
| Data | JSON replay | Native OTel |
| Metrics | 27 (11 + 16) | 14 (5 + 9) |
| Omitted in cluster (service) | — | `dag_file_queue_size`, `dagbag_size`, `dag_total_parse_time`, `dag_import_errors`, `dag_file_refresh_error`, `asset_updates` |
| Omitted in cluster (instance) | — | `pool_deferred/running`, `executor_queued` (instance only; service `executor_queued` retained), `asset_updates`, trigger blocked/failed/succeeded |

## Related docs

- [Airflow monitoring setup](../../../../docs/en/setup/backend/backend-airflow-monitoring.md)
- [SWIP-7 proposal](../../../../docs/en/swip/SWIP-7.md)
