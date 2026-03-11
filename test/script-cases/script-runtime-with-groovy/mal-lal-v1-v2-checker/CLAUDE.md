# MAL/LAL/Hierarchy v1-v2 Comparison Checker

Cross-version comparison tests that validate v2 (ANTLR4+Javassist) DSL compilers produce identical results to v1 (Groovy) compilers.

## Test Classes

| Class | Tests | Description |
|-------|-------|-------------|
| `MalComparisonTest` | ~1268 | Compiles and executes all MAL rules from 6 script directories |
| `LalComparisonTest` | 35 | Compiles and executes all LAL rules from script directories |
| `MalFilterComparisonTest` | 31 | Validates MAL filter operations (tagEqual, tagNotEqual, etc.) |
| `MalInputDataGeneratorTest` | 1 | Generates `.data.yaml` companion files for MAL rules |
| `MalExpectedDataGeneratorTest` | 1 | Generates expected sections in `.data.yaml` from v1 output |

## How It Works

For each DSL expression:
1. Compile with v1 (Groovy) and v2 (ANTLR4+Javassist)
2. Compare compile-time metadata (sample names, scope type, aggregation labels, etc.)
3. Execute both with identical mock input data
4. Assert output samples match (entities, labels, values)
5. Validate against expected data in `.data.yaml`

## Script Directories (MAL)

All under `test/script-cases/scripts/mal/`:

| Directory | Source | Rules |
|-----------|--------|-------|
| `test-meter-analyzer-config` | `server-starter/.../meter-analyzer-config/` | ~17 configs |
| `test-otel-rules` | `server-starter/.../otel-rules/` | ~73 service configs |
| `test-envoy-metrics-rules` | `server-starter/.../envoy-metrics-rules/` | 3 configs |
| `test-log-mal-rules` | `server-starter/.../log-mal-rules/` | 2 configs |
| `test-telegraf-rules` | `server-starter/.../telegraf-rules/` | 1 config (vm.yaml) |
| `test-zabbix-rules` | `server-starter/.../zabbix-rules/` | 1 config (agent.yaml) |

## Input Data Mock Principles

### MAL (.data.yaml files)

Each MAL rule YAML has a companion `.data.yaml` with `input` and `expected` sections.

**Input section:**
- Every metric referenced in expressions must have samples
- Label variants must cover all filter operations (tagEqual, tagNotEqual, tagMatch)
- Labels from entity function `['label']` args (e.g., `instance(['host_name'], ['service_instance_id'])`) must be present in ALL input samples — these determine scope/service/instance/endpoint entity extraction
- Numeric YAML keys (e.g., zabbix `1`, `2`) → use `String.valueOf()` in Java code

**Expected section:**
- Auto-generated from v1 (Groovy) execution output — v1 is the trusted baseline
- Rich assertions: entities (scope/service/instance/endpoint/layer), samples (labels/value)
- `error: 'v1 not-success'` means input data is broken — fix input, don't skip
- EMPTY results are hard failures

**YAML key variants:**
- Standard rules use `metricsRules` key
- Zabbix rules use `metrics` key (both are handled by the collector)

### LAL (.data.yaml files)

Each LAL rule YAML has a companion `.data.yaml` with per-rule test entries.

**Entry structure:** service, body-type, body, optional tags/extra-log, expect assertions.

**Expect assertions:** save, abort, service, instance, endpoint, layer, tag.*

**Proto-typed rules:** Use `extra-log.proto-class` + `extra-log.proto-json` for protobuf extraLog.

### Hierarchy

No data files — Service mock objects are built inline in test code.

## Generators

### MalInputDataGenerator

Extracts metric names and label requirements from compiled AST metadata. Generates `.data.yaml` input sections automatically. Run via `MalInputDataGeneratorTest` — skips files that already exist.

**Label sources extracted:**
- Compiled metadata: `aggregationLabels` and `scopeLabels` from `ExpressionMetadata`
- Tag filters: `tagEqual`, `tagNotEqual`, `tagMatch`, `tagNotMatch` arguments
- Closure access: `tags.label` and `tags['label']` property/bracket access
- Entity function arguments: `['label']` in `service()`, `instance()`, `endpoint()`, `process()` calls from `expSuffix`

**Entity function labels** are critical — `instance(['host_name'], ['service_instance_id'], Layer.MYSQL)` requires `service_instance_id` in every input sample. Without it, the entity extraction produces incorrect scope/service/instance values. The generator parses `expSuffix` to find these `['label']` arguments automatically.

### MalExpectedDataGenerator

Runs v1 engine on input data and captures output as expected baseline. Run via `MalExpectedDataGeneratorTest` — updates the `expected:` section in existing `.data.yaml` files.

## Adding New Rules

1. Copy the production YAML to the appropriate `test-*` directory
2. Run `MalInputDataGeneratorTest` to generate the `.data.yaml`
3. Review input data — add missing label variants for filters
4. Run `MalExpectedDataGeneratorTest` to generate expected sections
5. Run `MalComparisonTest` to verify all tests pass
6. Check for `error: 'v1 not-success'` in expected — fix input data

## Duplicate Rule Names

Some production configs (e.g., apisix.yaml) have duplicate rule names for route-based vs node-based variants. The collector disambiguates with `_2` suffix (e.g., `endpoint_http_status` → `endpoint_http_status_2`).

## K8s Mocking

Rules using `retagByK8sMeta` require K8s registry mocks. Both v1 and v2 K8sInfoRegistry are mocked via `Mockito.mockStatic()` in `@BeforeAll`. Mock `findServiceName(ns, pod)` returns `pod.ns`.

## Shared DSL Testing Framework (server-testing module)

Checker tests use utilities from `org.apache.skywalking.oap.server.testing.dsl`:

| Utility | Used by |
|---------|---------|
| `DslClassOutput.checkerTestDir(sourceFile)` | All three checkers — standardized `.generated-classes/` output dir |
| `DslRuleLoader.findScriptsDir(String...)` | `LalComparisonTest` — locate `test/script-cases/scripts/lal` |
| `LalRuleLoader.loadAllRules(Path)` | `LalComparisonTest` — load LAL rules with companion input data |
| `LalLogDataBuilder.buildLogData(Map)` | `LalComparisonTest` — build `LogData` from test input maps |
| `LalLogDataBuilder.buildExtraLog(Map)` | `LalComparisonTest` — build proto extraLog from input maps |
| `LalLogDataBuilder.buildSyntheticLogData(String)` | `LalComparisonTest` — synthetic LogData when no input data |

`LalComparisonTest` is fully migrated to the framework. `MalComparisonTest` uses `DslClassOutput` for class output dirs.
