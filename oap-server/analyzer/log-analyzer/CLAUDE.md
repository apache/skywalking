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
    spec/extractor/ExtractorSpec.java   — Extractor field setters (all methods take ctx explicitly)
    spec/sink/SinkSpec.java             — Sink spec (save/drop/sample)
    spec/sink/SamplerSpec.java          — Rate-limit sampler

  src/test/java/.../compiler/
    LALScriptParserTest.java            — 20 parser tests
    LALClassGeneratorTest.java          — 35 generator tests
    LALExpressionExecutionTest.java     — 27 data-driven execution tests (from YAML + .input.data)
```

## Package & Class Naming

All v2 classes live under `org.apache.skywalking.oap.log.analyzer.v2.*` to avoid FQCN conflicts with the v1 (Groovy) classes.

| Component | Package / Name |
|-----------|---------------|
| Parser/Model/Generator | `org.apache.skywalking.oap.log.analyzer.v2.compiler` |
| Generated classes | `org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalExpr_<N>` |
| Package holder | `org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalExpressionPackageHolder` |
| Runtime helper | `org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalRuntimeHelper` |
| Functional interface | `org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression` |

`<N>` is a global `AtomicInteger` counter.

## Single Class with Private Methods

The generator produces a single class per LAL script. Extractor and sink blocks become private methods called directly from `execute()` — no Consumer classes, no callback indirection.

Method naming: `_extractor`, `_extractor_2`, `_extractor_3` (no `_0` suffix for single methods).

Sub-blocks (slowSql, sampledTrace, metrics, sampler, rateLimit) are inlined within their parent method.

## Explicit Context Passing (No ThreadLocal)

All spec methods take `ExecutionContext ctx` as an explicit parameter — there is no `BINDING` ThreadLocal or `bind()` method. The `execute()` method receives `ctx` directly and passes it through:

- `execute(FilterSpec filterSpec, ExecutionContext ctx)` — entry point
- `filterSpec.json(ctx)`, `filterSpec.text(ctx)`, `filterSpec.sink(ctx)` — parser/sink calls
- `_e.service(h.ctx(), ...)`, `_e.tag(h.ctx(), ...)` — extractor calls via `h.ctx()`
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
| NONE + extraLogType | `parsed.response.code` | `((ExtraLogType) h.ctx().extraLog()).getResponse().getCode()` |
| NONE + no type | `parsed.x` | **Compile error — fails boot** |
| log fields | `log.service` | `h.ctx().log().getService()` |
| log trace | `log.traceContext.traceId` | `h.ctx().log().getTraceContext().getTraceId()` |
| tags | `tag("KEY")` | `h.tagValue("KEY")` |

### extraLogType and LALSourceTypeProvider SPI

For LAL rules with no DSL parser (`json{}`/`yaml{}`/`text{}`), the compiler needs a type to generate direct getter calls on `parsed.*` fields. Per-rule resolution order:

1. **DSL parser** (`json{}`, `yaml{}`, `text{}`) — parser wins, extraLogType is ignored
2. **Explicit `extraLogType`** in YAML rule config — FQCN string, resolved via `Class.forName()`
3. **`LALSourceTypeProvider` SPI** — default extraLogType for a layer, discovered via `ServiceLoader`
4. **Compile error** — if none of the above and the rule accesses `parsed.*`, boot fails

The SPI interface is in `org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider`. Receiver plugins implement it and register in `META-INF/services/`. Example: `EnvoyHTTPLALSourceTypeProvider` registers `HTTPAccessLogEntry` for `Layer.MESH`.

A single YAML file can have rules with different input types (e.g., `envoy-als.yaml` has a proto-based rule and a `json{}` rule, both in layer MESH). Resolution is per-rule, not per-file.

## Example

**Input**: `filter { json {} extractor { service parsed.service as String } sink {} }`

One class is generated:

```java
public class LalExpr_0 implements LalExpression {
    public void execute(FilterSpec filterSpec, ExecutionContext ctx) {
        LalRuntimeHelper h = new LalRuntimeHelper(ctx);
        filterSpec.json(ctx);
        if (!ctx.shouldAbort()) {
            _extractor(filterSpec.extractor(), h);
        }
        filterSpec.sink(ctx);
    }
    private void _extractor(ExtractorSpec _e, LalRuntimeHelper h) {
        _e.service(h.ctx(), h.toStr(h.mapVal("service")));
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
    execution-basic.yaml         — 17 LAL feature-coverage rules
    execution-basic.input.data   — mock input + expected output per rule
```

Each `.input.data` entry specifies `body-type`, `body`, optional `tags`, and `expect` assertions
(service, instance, endpoint, layer, tags, abort, save, timestamp, sampledTrace fields).

## Dependencies

All within this module (grammar, compiler, and runtime are merged):
- ANTLR4 grammar → generates lexer/parser at build time
- `LalExpression`, `ExecutionContext`, `FilterSpec`, all Spec classes — in `dsl` package of this module
- `javassist` — bytecode generation
