# LAL Compiler

Compiles LAL (Log Analysis Language) scripts into `LalExpression` implementation classes at runtime using ANTLR4 parsing and Javassist bytecode generation.

## Compilation Workflow

```
LAL DSL string
  → LALScriptParser.parse(dsl)                 [ANTLR4 lexer/parser → listener]
  → LALScriptModel (immutable AST)
  → LALClassGenerator.compileFromModel(model)
      1. detectParserType(model)     — compile-time data source analysis (JSON/YAML/TEXT/NONE)
      2. generateExecuteMethod()     — emit execute() + private methods (_extractor, _sink)
      3. classPool.makeClass()       — single class implementing LalExpression
      4. addLocalVariableTable()     — named LVT entries for all methods
      5. ctClass.toClass()           — load into JVM
  → LalExpression instance
```

The generated class implements:
```java
void execute(FilterSpec filterSpec, ExecutionContext ctx)
```

## File Structure

```
oap-server/analyzer/log-analyzer/
  src/main/antlr4/.../LALLexer.g4       — ANTLR4 lexer grammar
  src/main/antlr4/.../LALParser.g4      — ANTLR4 parser grammar

  src/main/java/.../compiler/
    LALScriptParser.java                — ANTLR4 facade: DSL string → AST
    LALScriptModel.java                 — Immutable AST model classes
    LALClassGenerator.java              — Public API, execute method codegen, class scaffolding
    LALBlockCodegen.java                — Extractor/sink/condition/value-access codegen
    LALCodegenHelper.java               — Static utility methods and shared constants
    rt/
      LalExpressionPackageHolder.java   — Class loading anchor (empty marker)
      LalRuntimeHelper.java             — Instance-based helper called by generated code

  src/main/java/.../dsl/
    LalExpression.java                  — Functional interface: execute(FilterSpec, ExecutionContext)
    ExecutionContext.java               — Per-log execution state (log, parsed, flags)
    DSL.java                            — Wraps compiled expression + FilterSpec
    spec/filter/FilterSpec.java         — Top-level filter spec (all methods take ctx explicitly)
    spec/extractor/MetricExtractor.java   — Handles LAL metrics {} blocks (prepare/submit samples to MAL)
    spec/sink/SinkSpec.java             — Sink spec (save/drop/sample)
    spec/sink/SamplerSpec.java          — Rate-limit sampler

  src/test/java/.../compiler/
    LALScriptParserTest.java            — 19 parser tests
    LALClassGeneratorTest.java          — 35 generator tests
    LALExpressionExecutionTest.java     — 25 data-driven execution tests (from YAML + .data.yaml)
```

## Package & Class Naming

All v2 classes live under `org.apache.skywalking.oap.log.analyzer.v2.*` to avoid FQCN conflicts with the v1 (Groovy) classes.

| Component | Package / Name |
|-----------|---------------|
| Parser/Model/Generator | `org.apache.skywalking.oap.log.analyzer.v2.compiler` |
| Generated classes | `org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.{yamlName}_L{lineNo}_{ruleName}` |
| Package holder | `org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalExpressionPackageHolder` |
| Runtime helper | `org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalRuntimeHelper` |
| Functional interface | `org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression` |

Class names are built from `yamlSource` (file name + line number) and `classNameHint` (rule name).
Example: `default_L3_default` (rule `default` at line 3 of `default.yaml`).
Falls back to `LalExpr_<N>` (global counter) when no hint is set.

## Single Class with Private Methods

The generator produces a single class per LAL script. Extractor and sink blocks become private methods called directly from `execute()` — no Consumer classes, no callback indirection.

Method naming: `_extractor`, `_extractor_2`, `_extractor_3` (no `_0` suffix for single methods).

Sub-blocks (metrics, sampler, rateLimit) are inlined within their parent method.

Note: `slowSql` and `sampledTrace` sub-DSLs have been removed from the grammar. Custom output
fields are now handled via the `outputType` mechanism with `outputFieldStatement` grammar rule.

## Explicit Context Passing (No ThreadLocal)

All spec methods take `ExecutionContext ctx` as an explicit parameter — there is no `BINDING` ThreadLocal or `bind()` method. The `execute()` method receives `ctx` directly and passes it through:

- `execute(FilterSpec filterSpec, ExecutionContext ctx)` — entry point
- `filterSpec.json(ctx)`, `filterSpec.text(ctx)`, `filterSpec.sink(ctx)` — parser/sink calls
- `((OutputType) h.ctx().output()).setService(...)` — standard field setters on the output builder
- `_e.prepareMetrics(h.ctx())`, `_e.submitMetrics(h.ctx(), _metrics)` — metrics calls via MetricExtractor
- `_f.sampler().rateLimit(h.ctx(), ...)` — sink calls via `h.ctx()`

The generated `execute()` method guards `_extractor()` and `_sink()` calls with `if (!ctx.shouldAbort())`, matching v1 Groovy behavior where `extractor {}` and `sink {}` closures check the abort flag before running their body. `finalizeSink(ctx)` also checks the flag. Individual spec methods inside each block additionally check `ctx.shouldAbort()` as a defense-in-depth measure.

## LocalVariableTable (LVT)

All generated methods include a `LocalVariableTable` attribute for debugger/decompiler readability. Without LVT, tools show `var0`, `var1`, `var2`, `var3` instead of named variables.

| Method | Slot 0 | Slot 1 | Slot 2 | Slot 3 |
|--------|--------|--------|--------|--------|
| `execute()` | `this` | `filterSpec` | `ctx` | `h` |
| `_extractor()` | `this` | `_e` | `h` | — |
| `_sink()` | `this` | `_f` | `h` | — |

LVT entries are added via `PrivateMethod` inner class which carries both source code and variable descriptors.

## Compile-Time Data Source Analysis

The generator detects the parser type from the AST at compile time and generates typed value access:

| Parser Type | LAL Example | Generated Code |
|---|---|---|
| JSON/YAML | `parsed.service` | `h.mapVal("service")` |
| JSON/YAML nested | `parsed.a.b` | `h.mapVal("a", "b")` |
| TEXT (regexp) | `parsed.level` | `h.group("level")` |
| NONE + inputType | `parsed.response.code` | `((ExtraLogType) h.ctx().extraLog()).getResponse().getCode()` |
| NONE + no inputType | `parsed.service` | `h.ctx().log().getService()` (LogData.Builder fallback) |
| log fields | `log.service` | `h.ctx().log().getService()` |
| log trace | `log.traceContext.traceId` | `h.ctx().log().getTraceContext().getTraceId()` |
| tags | `tag("KEY")` | `h.tagValue("KEY")` |

### inputType and LALSourceTypeProvider SPI

For LAL rules with no DSL parser (`json{}`/`yaml{}`/`text{}`), the compiler needs a type to generate direct getter calls on `parsed.*` fields. Per-rule resolution order:

1. **DSL parser** (`json{}`, `yaml{}`, `text{}`) — parser wins, inputType is ignored
2. **Explicit `inputType`** in YAML rule config — FQCN string, resolved via `Class.forName()`
3. **`LALSourceTypeProvider` SPI** — default inputType for a layer, discovered via `ServiceLoader`
4. **`LogData.Builder` fallback** — if none of the above, `parsed.*` generates getter chains on `LogData.Builder` with compile-time reflection validation. Fields not found on `LogData.Builder` cause `IllegalArgumentException` at boot.

The SPI interface is in `org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider`. Receiver plugins implement it and register in `META-INF/services/`. Example: `EnvoyHTTPLALSourceTypeProvider` registers `HTTPAccessLogEntry` for `Layer.MESH`.

Resolution is per-rule, not per-file.

### outputType — Configurable Output Entity

The output entity type (`AbstractLog` subclass) produced by the LAL sink. Resolution order:

1. **Per-rule YAML config** `outputType` field — FQCN string, highest priority
2. **`LALSourceTypeProvider` SPI** `outputType()` — default for a layer
3. **`Log.class`** — fallback if not specified anywhere

inputType is per-layer (all rules share the same input proto), but outputType is per-rule
(different rules in the same layer may produce different output types).

### Output Field Assignments

Custom fields specific to the output subclass are set via `outputFieldStatement` in the extractor:

```
extractor {
  statement parsed.statement as String    // output field — direct setter on output object
  latency parsed.latency as Long          // output field
  service parsed.service as String        // standard field — direct setter on output builder
}
```

Unknown identifiers in the extractor (not `service`, `instance`, `endpoint`, `layer`, etc.)
are parsed as `OutputFieldAssignment`. The compiler generates direct setter calls:
`((OutputType) h.ctx().output()).setFieldName(value)`.

**Output object creation**: The generated `execute()` method creates the output object at the
start: `h.ctx().setOutput(new OutputType())`. This happens before the extractor runs, so
output fields are set directly via typed setter calls — no reflection.

**Compile-time validation**: `outputType` must be set for output field assignments. The compiler
validates that a matching setter exists on the output type class (e.g., `setStatement(String)`).
If no setter is found, compilation fails with an `IllegalArgumentException` at boot.

**Runtime dispatch**: `RecordSinkListener.parse()` reads the output object from
`ExecutionContext.output()` (already populated by generated code), calls `init()` for
builder mode, then `build()` dispatches via `complete()` or `sourceReceiver.receive()`.

Note: `slowSql {}` and `sampledTrace {}` sub-DSLs were removed. Custom output types use
the `outputType` + output field mechanism instead of dedicated DSL blocks.

## Example

**Input**: `filter { json {} extractor { service parsed.service as String } sink {} }`

One class is generated (e.g., `default_L3_my_rule` when `yamlSource=default.yaml:3`):

```java
public class default_L3_my_rule implements LalExpression {
    public void execute(FilterSpec filterSpec, ExecutionContext ctx) {
        LalRuntimeHelper h = new LalRuntimeHelper(ctx);
        filterSpec.json(ctx);
        if (!ctx.shouldAbort()) {
            _extractor(filterSpec.extractor(), h);
        }
        filterSpec.sink(ctx);
    }
    private void _extractor(MetricExtractor _e, LalRuntimeHelper h) {
        ((LogBuilder) h.ctx().output()).setService(h.toStr(h.mapVal("service")));
    }
}
```

## Runtime Helper (LalRuntimeHelper)

Instance-based helper created at the start of `execute()`, holds the `ExecutionContext`.

**Data source methods:**
- `mapVal(key)`, `mapVal(k1, k2)`, `mapVal(k1, k2, k3)` — JSON/YAML map access
- `group(name)` — text regexp named group
- `tagValue(key)` — log tag lookup
- `ctx()` — access to ExecutionContext (for `h.ctx().log()` proto getters)

**Type conversion:** `toStr()`, `toLong()`, `toInt()`, `toBool()`

**Boolean evaluation:** `isTrue()`, `isNotEmpty()`

**Safe navigation:** `toString()`, `trim()`

## JSON/YAML LogData Field Population

When `json{}` or `yaml{}` parses the log body, `FilterSpec` also adds LogData proto fields
(`service`, `serviceInstance`, `endpoint`, `layer`, `timestamp`) to the parsed map via
`putIfAbsent`. Body-parsed values take priority; proto fields serve as fallback. This matches
v1 Groovy `Binding.Parsed.getAt(key)` behavior where `parsed.service` falls back to
`LogData.getService()` when the JSON body doesn't contain a `service` key.

## Null-Safe String Conversion

Generated code calls `h.toStr()` instead of `String.valueOf()` for casting parsed values to String.
This preserves Java `null` for missing fields (matching Groovy's `null as String` → `null` behavior),
whereas `String.valueOf(null)` would produce the string `"null"`.

## Data-Driven Execution Tests

`LALExpressionExecutionTest` loads LAL rules from YAML and mock input from `.input.data` files:

```
test/script-cases/scripts/lal/test-lal/
  oap-cases/                     — copies of shipped LAL configs (each with .input.data)
  feature-cases/
    execution-basic.yaml         — 16 LAL feature-coverage rules
    execution-basic.data.yaml    — mock input + expected output per rule
```

Each `.input.data` entry specifies `body-type`, `body`, optional `tags`, and `expect` assertions
(service, instance, endpoint, layer, tags, abort, save, timestamp).

## LAL Input Data Mock Principles

LAL test data lives in `.input.data` files alongside rule YAML files under `test/script-cases/scripts/lal/`. Each entry describes one log to process and the expected output.

### Input Entry Structure

```yaml
rule-name:
  - service: test-svc               # LogData.service
    instance: test-inst              # LogData.serviceInstance (optional)
    body-type: json|yaml|text|none   # How to parse the body
    body: '{"key": "value"}'         # Log body string
    trace-id: trace-001              # Trace context (optional)
    timestamp: 1609459200000         # LogData.timestamp (optional)
    tags:                            # LogData tags (optional)
      LOG_KIND: NET_PROFILING_SAMPLED_TRACE
    extra-log:                       # For proto-typed rules (e.g., envoy-als)
      proto-class: io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry
      proto-json: '{"response":{"responseCode":500}}'
    expect:                          # Expected output assertions
      save: true                     # SinkSpec.save() called
      abort: false                   # Not aborted
      service: expected-svc          # Extracted service name
      layer: MESH                    # Extracted layer
      tag.status.code: "500"         # Extracted tag value
```

### Principles

1. **`body-type` determines parsing**: `json` → `json{}` block, `text` → `text{}` block, `none` → proto extraLog or raw LogData access.
2. **`extra-log` for proto types**: When rules access `parsed.*` on protobuf types (e.g., `HTTPAccessLogEntry`), provide `proto-class` and `proto-json`. The test harness parses via `JsonFormat`.
3. **`expect` section is mandatory**: Every entry must have `expect` with at least `save` and `abort`.
4. **Tag assertions**: `tag.KEY` in expect asserts extracted tag values (e.g., `tag.status.code: "500"`).
5. **v1 is the truth**: Both v1 (Groovy) and v2 (ANTLR4) must produce identical results. If v1 produces different output than expected, the expected data has a bug.

### Directory Structure

```
test/script-cases/scripts/lal/test-lal/
  oap-cases/                     — copies of shipped LAL configs
    default.yaml / default.input.data
    envoy-als.yaml / envoy-als.input.data
    ...
  feature-cases/
    execution-basic.yaml / execution-basic.input.data  — LAL feature tests
```

## Debug Output

When `SW_DYNAMIC_CLASS_ENGINE_DEBUG=true` environment variable is set, generated `.class` files are written to disk for inspection:

```
{skywalking}/lal-rt/
  *.class          - Generated LalExpression .class files
```

This is the same env variable used by OAL. Useful for debugging code generation issues or comparing V1 vs V2 output. In tests, use `setClassOutputDir(dir)` instead.

## Dependencies

All within this module (grammar, compiler, and runtime are merged):
- ANTLR4 grammar → generates lexer/parser at build time
- `LalExpression`, `ExecutionContext`, `FilterSpec`, all Spec classes — in `dsl` package of this module
- `javassist` — bytecode generation
