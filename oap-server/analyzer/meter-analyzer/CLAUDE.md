# MAL Compiler

Compiles MAL (Meter Analysis Language) expressions into `MalExpression` implementation classes at runtime using ANTLR4 parsing and Javassist bytecode generation.

## Compilation Workflow

```
MAL expression string
  → MALScriptParser.parse(expression)          [ANTLR4 lexer/parser → visitor]
  → MALExpressionModel.Expr (immutable AST)
  → MALClassGenerator.compileFromModel(name, ast)
      1. collectClosures(ast)          — pre-scan AST for closure arguments
      2. makeCompanionClass()          — one companion per closure, implements functional interface
                                         with closure body inlined directly in SAM method
      3. classPool.makeClass()         — create main class implementing MalExpression
      4. generateRunMethod()           — emit Java source for run(Map<String,SampleFamily>)
      5. toClass() companions first    — static initializer on main class references companion ctors
      6. ctClass.toClass(MalExpressionPackageHolder.class)  — load main class
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
    MALClassGenerator.java            — Public API + orchestration (compile, compileFilter, compileFromModel)
    MALExprCodegen.java               — Expression → Java source (variable-per-expression model)
    MALMethodChainCodegen.java        — Method chain codegen: built-in .method() + extension ::method() + args
    MALMetadataExtractor.java         — Static AST analysis → ExpressionMetadata + metadata() source
    MALBytecodeHelper.java            — Javassist: class naming, debug output, LineNumberTable, LocalVariableTable
    MALClosureCodegen.java            — Companion class codegen: closure body inlined in SAM method
    MALCodegenHelper.java             — Static utility methods and shared constants (SF, RUN_VAR, escapeJava)
    rt/
      MalExpressionPackageHolder.java — Class loading anchor (empty marker)
      MalRuntimeHelper.java           — Static helpers called by generated code (divReverse, regexMatch, isTruthy)
      MalExtensionRegistry.java       — SPI registry for extension functions (namespace::method)

  src/main/java/.../spi/
    MalFunctionExtension.java         — SPI interface for extension namespaces
    MALContextFunction.java           — Annotation marking callable methods

  src/test/java/.../compiler/
    MALScriptParserTest.java          — 22 parser tests
    MALClassGeneratorTest.java        — 20 generator tests
    MALExtensionFunctionTest.java     — 9 extension SPI tests
```

## Package & Class Naming

All v2 classes live under `org.apache.skywalking.oap.meter.analyzer.v2.*` to avoid FQCN conflicts with the v1 (Groovy) classes.

| Component | Package / Name |
|-----------|---------------|
| Parser/Model/Generator | `org.apache.skywalking.oap.meter.analyzer.v2.compiler` |
| Generated classes | `org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.{yamlName}_L{lineNo}_{ruleName}` |
| Companion classes | `org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.{yamlName}_L{lineNo}_{ruleName}$_{closureField}` |
| Filter classes | `org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.{yamlName}_L{lineNo}_filter` |
| Package holder | `org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalExpressionPackageHolder` |
| Runtime helper | `org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalRuntimeHelper` |
| Extension registry | `org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalExtensionRegistry` |
| Extension SPI | `org.apache.skywalking.oap.meter.analyzer.v2.spi.MalFunctionExtension` |
| Extension annotation | `org.apache.skywalking.oap.meter.analyzer.v2.spi.MALContextFunction` |
| Functional interface | `org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression` |

Class names are built from `yamlSource` (file name + line number) and `classNameHint` (rule name or `filter`).
Example: `vm_L25_cpu_total_percentage` (expression), `gateway_service_L33_filter` (filter).
Falls back to `MalExpr_<N>` (global counter) when no hint is set.

## Extension Function SPI (`namespace::method()`)

MAL supports custom extension functions via the `namespace::method()` syntax. Extensions are discovered
at startup via `java.util.ServiceLoader`.

### Syntax

```
metric.sum(['svc']).genai::estimateCost()
metric.test::scale(2.0)
```

The `::` separator distinguishes extension calls from built-in `SampleFamily` methods. The namespace
avoids global method name conflicts — method names only need to be unique within their namespace.

### Implementing an Extension

1. Create a class implementing `MalFunctionExtension` with **static** `@MALContextFunction` methods:

```java
public class MyExtension implements MalFunctionExtension {
    @Override
    public String name() { return "myext"; }

    @MALContextFunction
    public static SampleFamily transform(SampleFamily sf, double factor) {
        return sf.multiply(Double.valueOf(factor));
    }

    @MALContextFunction
    public static SampleFamily filterTag(SampleFamily sf, String key, String value) {
        return sf.tagEqual(key, value);
    }
}
```

2. Register via SPI file `META-INF/services/org.apache.skywalking.oap.meter.analyzer.v2.spi.MalFunctionExtension`:
```
com.example.MyExtension
```

3. Use in MAL scripts: `.myext::transform(2.0)`, `.myext::filterTag("env", "prod")`

### Method Requirements

- Methods **must** be `static` (non-static methods throw `IllegalArgumentException` at startup)
- First parameter **must** be `SampleFamily` (auto-bound to the current chain value)
- Return type **must** be `SampleFamily`
- Additional parameters are matched from MAL arguments by type:

| Java Type | MAL Argument |
|-----------|-------------|
| `String` | String literal: `"value"` |
| `double` / `int` | Number literal: `2.0`, `100` |
| `List<String>` | String list: `["tag1", "tag2"]` |

### Compile-Time Validation

The compiler validates at expression compilation time:
- Namespace exists in registry
- Method exists in that namespace
- Argument count matches (excluding the implicit `SampleFamily` first param)
- Argument types are compatible with the method signature

### Generated Code

The compiler generates direct static method calls — no reflection or registry dispatch at runtime.
Each metric gets its own named variable (e.g. `_metric`).

For `metric.sum(['svc']).myext::transform(2.0)`:
```java
SampleFamily _metric = ((SF) samples.getOrDefault("metric", SF.EMPTY));
_metric = _metric.sum(java.util.Arrays.asList(new String[]{"svc"}));
_metric = com.example.MyExtension.transform(_metric, 2.0);
```

For zero-arg extensions like `metric.myext::noop()`:
```java
SampleFamily _metric = ((SF) samples.getOrDefault("metric", SF.EMPTY));
_metric = com.example.MyExtension.noop(_metric);
```

## Javassist Constraints

- **No anonymous inner classes**: Javassist cannot compile `new Consumer() { ... }` or `new Function() { ... }` in method bodies.
- **No lambda expressions**: Javassist has no lambda support.
- **Closure approach**: Each closure becomes a companion class (e.g., `MainClass$_tag`) that directly implements the functional interface. The closure body is inlined in the SAM method. The main class holds a `public static final` field for each closure, initialized in a `static {}` block via `new CompanionClass()`. No reflection or `LambdaMetafactory` at runtime. One extra `.class` file is produced per closure.
- **Inner class notation**: Use `$` not `.` for nested classes (e.g., `SampleFamilyFunctions$TagFunction`).
- **`isPresent()`/`get()` instead of `ifPresent()`**: `ifPresent(Consumer)` would require an anonymous class. Use `Optional.isPresent()` + `Optional.get()` pattern.
- **Closure interface dispatch**: Different closure call sites use different functional interfaces:
  - `tag({ ... })` → `SampleFamilyFunctions$TagFunction`
  - `forEach(closure)` / `serviceRelation(closure)` etc. → `SampleFamilyFunctions$ForEachFunction`
  - `instance(closure)` → `SampleFamilyFunctions$PropertiesExtractor`
  - `decorate(closure)` → `SampleFamilyFunctions$DecorateFunction`
- **v2 package isolation**: All v2 classes are under `*.v2.*` packages, so there are no FQCN conflicts with the v1 Groovy module.

## Code Generation Model

Uses **variable-per-expression**: each metric gets its own named variable (`_metricName`).
Chain calls reassign to the same variable. Binary operations combine variables directly.
No shared mutable variable, no save/restore.

### Simple expression

**Input**: `instance_jvm_cpu.sum(['service', 'instance'])`

**Generated `run()` method**:
```java
public SampleFamily run(java.util.Map samples) {
  SampleFamily _instance_jvm_cpu = ((SampleFamily) samples.getOrDefault("instance_jvm_cpu", SampleFamily.EMPTY));
  _instance_jvm_cpu = _instance_jvm_cpu.sum(java.util.Arrays.asList(new String[]{"service", "instance"}));
  return _instance_jvm_cpu;
}
```

### Binary expression

**Input**: `metric1.sum(['svc']) + metric2.avg(['svc'])`

```java
public SampleFamily run(java.util.Map samples) {
  SampleFamily _metric1 = ((SampleFamily) samples.getOrDefault("metric1", SampleFamily.EMPTY));
  _metric1 = _metric1.sum(java.util.Arrays.asList(new String[]{"svc"}));
  SampleFamily _metric2 = ((SampleFamily) samples.getOrDefault("metric2", SampleFamily.EMPTY));
  _metric2 = _metric2.avg(java.util.Arrays.asList(new String[]{"svc"}));
  _metric1 = _metric1.plus(_metric2);
  return _metric1;
}
```

### Extension function

**Input**: `metric.sum(['svc']).test::scale(2.0)`

```java
public SampleFamily run(java.util.Map samples) {
  SampleFamily _metric = ((SampleFamily) samples.getOrDefault("metric", SampleFamily.EMPTY));
  _metric = _metric.sum(java.util.Arrays.asList(new String[]{"svc"}));
  _metric = TestMalExtension.scale(_metric, 2.0);
  return _metric;
}
```

### Closure

**Input**: `metric.tag({ tags -> tags['k'] = 'v' })`

Two classes are generated (e.g., `vm_L5_my_metric` when `yamlSource=vm.yaml:5`):

Main class `vm_L5_my_metric`:
- `public static final TagFunction _tag;`
- `static { _tag = new vm_L5_my_metric$_tag(); }`
- `run()` body: `_metric = _metric.tag(_tag);`

Companion class `vm_L5_my_metric$_tag implements TagFunction`:
- `public Object apply(Object _raw) { Map tags = (Map) _raw; tags.put("k", "v"); return tags; }`

## ExpressionMetadata (replaces ExpressionParsingContext)

Metadata is extracted statically from the AST at compile time by `MALMetadataExtractor.extractMetadata()`. No ThreadLocal, no dry-run execution. The `Analyzer` calls `expression.metadata()` to get sample names, scope type, aggregation labels, downsampling, histogram/percentile info.

## Debug Output

When `SW_DYNAMIC_CLASS_ENGINE_DEBUG=true` environment variable is set, generated `.class` files are written to disk for inspection:

```
{skywalking}/mal-rt/
  *.class          — Main MalExpression class per expression
  *$_tag.class     — Companion class per closure (one per tag/forEach/instance/decorate call)
```

This is the same env variable used by OAL. Useful for debugging code generation issues or comparing V1 vs V2 output. In tests, use `setClassOutputDir(dir)` instead.

## MAL Input Data Mock Principles

MAL test data lives in `.data.yaml` companion files alongside rule YAML files under `oap-server/analyzer/dsl-scripts-test/src/test/resources/scripts/mal/`. Each `.data.yaml` has two sections: `input` (mock samples) and `expected` (v1-verified output assertions).

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

## Testing Framework (server-testing module)

Test utilities from `org.apache.skywalking.oap.server.testing.dsl`:

- `DslClassOutput.unitTestDir("mal")` — output dir for unit tests (`target/mal-generated-classes/`)
- `DslClassOutput.checkerTestDir(sourceFile)` — output dir for checker tests (`{baseName}.generated-classes/`)
- `MalRuleLoader.loadAllRules(Path, String[])` — loads all MAL rules with companion `.data.yaml`
- `MalRuleLoader.formatExp(expPrefix, expSuffix, exp)` — replicates production `MetricConvert.formatExp()`
- `MalMockDataBuilder.buildFromInput(Map, double)` — builds version-neutral `MalMockSample` from input
- `DslRuleLoader.findScriptsDir(String...)` — resolves scripts directory from candidates
- `DslRuleLoader.findRuleLine(String[], String, int)` — finds 1-based line number of rule in YAML

Used by `MalComparisonTest` and `MalFilterComparisonTest`.

## Dependencies

All within this module (grammar, compiler, and runtime are merged):
- ANTLR4 grammar → generates lexer/parser at build time
- `MalExpression`, `ExpressionMetadata`, `SampleFamily` — in `dsl` package of this module
- `javassist` — bytecode generation
