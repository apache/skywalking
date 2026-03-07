# MAL Compiler

Compiles MAL (Meter Analysis Language) expressions into `MalExpression` implementation classes at runtime using ANTLR4 parsing and Javassist bytecode generation.

## Compilation Workflow

```
MAL expression string
  → MALScriptParser.parse(expression)          [ANTLR4 lexer/parser → visitor]
  → MALExpressionModel.Expr (immutable AST)
  → MALClassGenerator.compileFromModel(name, ast)
      1. collectClosures(ast)          — pre-scan for closure arguments
      2. addClosureMethod()            — add closure body as method on main class
      3. classPool.makeClass()         — create main class implementing MalExpression
      4. generateRunMethod()           — emit Java source for run(Map<String,SampleFamily>)
      5. ctClass.toClass(MalExpressionPackageHolder.class)  — load via package anchor
      6. wire closure fields via LambdaMetafactory (no extra .class files)
  → MalExpression instance
```

The generated class implements `MalExpression`:
```java
SampleFamily run(Map<String, SampleFamily> samples)  // pure computation, no side effects
ExpressionMetadata metadata()                         // compile-time metadata from AST
```

## File Structure

```
oap-server/analyzer/meter-analyzer/
  src/main/antlr4/.../MALLexer.g4     — ANTLR4 lexer grammar
  src/main/antlr4/.../MALParser.g4    — ANTLR4 parser grammar

  src/main/java/.../compiler/
    MALScriptParser.java              — ANTLR4 facade: expression → AST
    MALExpressionModel.java           — Immutable AST model classes
    MALClassGenerator.java            — Public API, run method codegen, metadata extraction
    MALClosureCodegen.java            — Closure method codegen (inlined on main class via LambdaMetafactory)
    MALCodegenHelper.java             — Static utility methods and shared constants
    rt/
      MalExpressionPackageHolder.java — Class loading anchor (empty marker)
      MalRuntimeHelper.java           — Static helpers called by generated code (divReverse, regexMatch, isTruthy)

  src/test/java/.../compiler/
    MALScriptParserTest.java          — 20 parser tests
    MALClassGeneratorTest.java        — 32 generator tests
```

## Package & Class Naming

All v2 classes live under `org.apache.skywalking.oap.meter.analyzer.v2.*` to avoid FQCN conflicts with the v1 (Groovy) classes.

| Component | Package / Name |
|-----------|---------------|
| Parser/Model/Generator | `org.apache.skywalking.oap.meter.analyzer.v2.compiler` |
| Generated classes | `org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.{yamlName}_L{lineNo}_{ruleName}` |
| Filter classes | `org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.{yamlName}_L{lineNo}_filter` |
| Package holder | `org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalExpressionPackageHolder` |
| Runtime helper | `org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalRuntimeHelper` |
| Functional interface | `org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression` |

Class names are built from `yamlSource` (file name + line number) and `classNameHint` (rule name or `filter`).
Example: `vm_L25_cpu_total_percentage` (expression), `gateway_service_L33_filter` (filter).
Falls back to `MalExpr_<N>` (global counter) when no hint is set.

## Javassist Constraints

- **No anonymous inner classes**: Javassist cannot compile `new Consumer() { ... }` or `new Function() { ... }` in method bodies.
- **No lambda expressions**: Javassist has no lambda support.
- **Closure approach**: Closure bodies are compiled as methods on the main class (e.g., `_tag_apply(Map)`), then wrapped via `LambdaMetafactory` into functional interface instances. No extra `.class` files are produced — the JVM creates hidden classes internally (same mechanism `javac` uses for lambdas).
- **Inner class notation**: Use `$` not `.` for nested classes (e.g., `SampleFamilyFunctions$TagFunction`).
- **`isPresent()`/`get()` instead of `ifPresent()`**: `ifPresent(Consumer)` would require an anonymous class. Use `Optional.isPresent()` + `Optional.get()` pattern.
- **Closure interface dispatch**: Different closure call sites use different functional interfaces:
  - `tag({ ... })` → `SampleFamilyFunctions$TagFunction`
  - `forEach(closure)` / `serviceRelation(closure)` etc. → `SampleFamilyFunctions$ForEachFunction`
  - `instance(closure)` → `SampleFamilyFunctions$PropertiesExtractor`
  - `decorate(closure)` → `SampleFamilyFunctions$DecorateFunction`
- **v2 package isolation**: All v2 classes are under `*.v2.*` packages, so there are no FQCN conflicts with the v1 Groovy module.

## Example

**Input**: `instance_jvm_cpu.sum(['service', 'instance'])`

**Generated `run()` method** (pure computation, no ThreadLocal):
```java
public SampleFamily run(Map samples) {
  return ((SampleFamily) samples.getOrDefault("instance_jvm_cpu", SampleFamily.EMPTY))
      .sum(java.util.List.of("service", "instance"));
}
```

**Generated `metadata()` method** (returns compile-time facts extracted from AST):
```java
public ExpressionMetadata metadata() {
  // samples=["instance_jvm_cpu"], aggregationLabels=["service","instance"], ...
  return new ExpressionMetadata(...);
}
```

**Input with closure**: `metric.tag({ tags -> tags['k'] = 'v' })`

One class is generated (e.g., `vm_L5_my_metric` when `yamlSource=vm.yaml:5`):
- Method `_tag_apply(Map tags)` — contains `tags.put("k", "v"); return tags;`
- Field `_tag` — typed as `TagFunction`, wired via `LambdaMetafactory` after class loading
- `run()` body calls `metric.tag(this._tag)`

## ExpressionMetadata (replaces ExpressionParsingContext)

Metadata is extracted statically from the AST at compile time by `MALClassGenerator.extractMetadata()`. No ThreadLocal, no dry-run execution. The `Analyzer` calls `expression.metadata()` to get sample names, scope type, aggregation labels, downsampling, histogram/percentile info.

## Debug Output

When `SW_DYNAMIC_CLASS_ENGINE_DEBUG=true` environment variable is set, generated `.class` files are written to disk for inspection:

```
{skywalking}/mal-rt/
  *.class          - Generated MalExpression .class files (one per expression, no separate closure classes)
```

This is the same env variable used by OAL. Useful for debugging code generation issues or comparing V1 vs V2 output. In tests, use `setClassOutputDir(dir)` instead.

## MAL Input Data Mock Principles

MAL test data lives in `.data.yaml` companion files alongside rule YAML files under `test/script-cases/scripts/mal/`. Each `.data.yaml` has two sections: `input` (mock samples) and `expected` (v1-verified output assertions).

### Input Section Principles

1. **Every metric referenced in rule expressions must have samples** — missing metrics produce EMPTY results (hard test failure).
2. **Label variants for filters**: If a rule uses `tagEqual('cpu', 'cpu-total')`, the input must have samples with `cpu: cpu-total`. If another rule in the same file uses `tagNotEqual('cpu', 'cpu-total')`, there must also be samples with a different `cpu` value (e.g., `cpu: cpu0`).
3. **`host` label for `service(['host'])`**: Rules with `expSuffix: service(['host'], ...)` derive the service entity name from the `host` label. All input samples should include a `host` label so the entity service name is non-empty.
4. **Numeric YAML keys**: Some configs (e.g., zabbix `agent.yaml`) use numeric label keys like `1`, `2`. YAML parsers read these as `Integer`, not `String`. Test code must use `String.valueOf()` on both keys and values when building label maps.
5. **Auto-generation**: `MalInputDataGenerator` extracts metric names and label requirements from compiled expression metadata. Run `MalInputDataGeneratorTest` to generate `.data.yaml` files for new rules. It skips files that already exist — delete the `.data.yaml` to regenerate.

### Expected Section Principles

1. **v1 is the truth**: The expected data is auto-generated by running the v1 (Groovy) engine on the input data. v1 is production-verified, so its output is the ground truth.
2. **Non-empty output required**: If v1 produces EMPTY, the input data has a bug. Fix the input, not skip the test.
3. **Rich assertions**: Expected includes entities (scope, service, instance, endpoint, layer) and samples (labels, value). Not just `min_samples: 1`.
4. **Error markers**: `error: 'v1 not-success'` means v1 failed to execute the expression. Fix the input data so v1 succeeds.
5. **Re-generation**: Run `MalExpectedDataGeneratorTest` to update expected sections after input data changes.

### Directory Structure

| Directory | Source |
|-----------|--------|
| `test-meter-analyzer-config` | `oap-server/server-starter/.../meter-analyzer-config/` |
| `test-otel-rules` | `oap-server/server-starter/.../otel-rules/` |
| `test-envoy-metrics-rules` | `oap-server/server-starter/.../envoy-metrics-rules/` |
| `test-log-mal-rules` | `oap-server/server-starter/.../log-mal-rules/` |
| `test-telegraf-rules` | `oap-server/server-starter/.../telegraf-rules/` |
| `test-zabbix-rules` | `oap-server/server-starter/.../zabbix-rules/` |

### YAML Key Variants

| Key | Used by |
|-----|---------|
| `metricsRules` | Standard rule YAMLs (OTEL, meter-analyzer, envoy, log-mal, telegraf) |
| `metrics` | Zabbix `agent.yaml` (production ZabbixConfig maps `metrics` to `getMetricsRules()`) |

## Dependencies

All within this module (grammar, compiler, and runtime are merged):
- ANTLR4 grammar → generates lexer/parser at build time
- `MalExpression`, `ExpressionMetadata`, `SampleFamily` — in `dsl` package of this module
- `javassist` — bytecode generation
