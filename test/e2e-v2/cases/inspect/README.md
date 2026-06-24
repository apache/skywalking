# Inspect API e2e — aware (existing) + foreign-metric (new) paths

Two independent OAPs share one storage backend (no cluster):

- **oap-a** loads `otel-rules/inspect-e2e.yaml`, turning the OTLP emitter's
  `e2e_rr_pool_size` into the Service metric `meter_inspect_e2e_pool`.
- **oap-b** loads no such rule, so that metric is **foreign** to it — absent from its
  local registry but present in the shared storage.

`inspect-foreign-flow.sh` drives both paths and asserts inline:

| Path | OAP | Assertion |
|------|-----|-----------|
| aware (existing) | oap-a | `/inspect/metrics` lists it; `/inspect/entities` returns `inspect-e2e-svc` with an `mqeEntity` |
| — | oap-b | `/inspect/metrics` excludes it |
| aware, no metadata | oap-b | `/inspect/entities` → `400 metric unknown locally …` |
| **foreign entity (new)** | oap-b | `/inspect/entities --value-column --value-type` returns the same entity, `scope:null`, no `mqeEntity` |
| **foreign value (new)** | oap-b | `POST /inspect/values` with `foreignMetrics` returns the metric's value series (`42`) |

Covered storages: `banyandb/`, `elasticsearch/`, `postgresql/`.

## CI wiring (gated on skywalking-cli)

The foreign assertions call `swctl admin inspect entities --value-column / --value-type`
(flags from [skywalking-cli #230](https://github.com/apache/skywalking-cli/pull/230)) and
`swctl admin inspect values --foreign-metric` (command from
[skywalking-cli #232](https://github.com/apache/skywalking-cli/pull/232)). The e2e builds swctl
from `SW_CTL_COMMIT` (`test/e2e-v2/script/env`), pinned to a cli commit that includes both, and the
three storage variants are wired into the `e2e` matrix in `.github/workflows/skywalking.yaml`.

To validate locally, build swctl from that commit (or newer) and run any variant's
`e2e.yaml` with `skywalking-infra-e2e`; all three storages pass.
