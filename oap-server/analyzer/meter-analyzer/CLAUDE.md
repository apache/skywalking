# MAL Compiler

Compiles MAL (Meter Analysis Language) expressions into `MalExpression` implementation classes at runtime using ANTLR4 parsing and Javassist bytecode generation.

## Compilation Workflow

```
MAL expression string
  → MALScriptParser.parse(expression)          [ANTLR4 lexer/parser → visitor]
  → MALExpressionModel.Expr (immutable AST)
  → MALClassGenerator.compileFromModel(name, ast)
      1. collectClosures(ast)          — pre-scan for closure arguments
      2. compileClosureClass()         — generate each closure as a separate Javassist class
      3. classPool.makeClass()         — create main class implementing MalExpression
      4. generateRunMethod()           — emit Java source for run(Map<String,SampleFamily>)
      5. ctClass.toClass(MalExpressionPackageHolder.class)  — load via package anchor
      6. wire closure fields via reflection
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
    MALClassGenerator.java            — Javassist code generator
    rt/
      MalExpressionPackageHolder.java — Class loading anchor (empty marker)
      MalRuntimeHelper.java           — Static helpers called by generated code (e.g., divReverse)

  src/test/java/.../compiler/
    MALScriptParserTest.java          — 20 parser tests
    MALClassGeneratorTest.java        — 28 generator tests
```

## Package & Class Naming

| Component | Package / Name |
|-----------|---------------|
| Parser/Model/Generator | `org.apache.skywalking.oap.meter.analyzer.compiler` |
| Generated classes | `org.apache.skywalking.oap.meter.analyzer.compiler.rt.MalExpr_<N>` |
| Closure classes | `org.apache.skywalking.oap.meter.analyzer.compiler.rt.MalExpr_<N>_Closure<M>` |
| Package holder | `org.apache.skywalking.oap.meter.analyzer.compiler.rt.MalExpressionPackageHolder` |
| Runtime helper | `org.apache.skywalking.oap.meter.analyzer.compiler.rt.MalRuntimeHelper` |
| Functional interface | `org.apache.skywalking.oap.meter.analyzer.dsl.MalExpression` (in meter-analyzer) |

`<N>` is a global `AtomicInteger` counter. `<M>` is the closure index within the expression.

## Javassist Constraints

- **No anonymous inner classes**: Javassist cannot compile `new Consumer() { ... }` or `new Function() { ... }` in method bodies. Closures are pre-compiled as separate `CtClass` instances, stored as fields (`_closure0`, `_closure1`, ...) on the main class, and wired via reflection after `toClass()`.
- **No lambda expressions**: Use the separate-class approach above.
- **Inner class notation**: Use `$` not `.` for nested classes (e.g., `SampleFamilyFunctions$TagFunction`).
- **`isPresent()`/`get()` instead of `ifPresent()`**: `ifPresent(Consumer)` would require an anonymous class. Use `Optional.isPresent()` + `Optional.get()` pattern.
- **Closure interface dispatch**: Different closure call sites use different functional interfaces:
  - `tag({ ... })` → `SampleFamilyFunctions$TagFunction`
  - `forEach(closure)` / `serviceRelation(closure)` etc. → `SampleFamilyFunctions$ForEachFunction`
  - `instance(closure)` → `SampleFamilyFunctions$PropertiesExtractor`
- **No new v2 code in shared DSL classes**: New runtime behavior used by generated code goes in `MalRuntimeHelper` (in the `compiler.rt` package) to avoid FQCN conflicts with the v1 Groovy module which shares the same `dsl` package.

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

Two classes are generated:
1. `MalExpr_0_Closure0` — implements `TagFunction` with `Map apply(Map tags) { tags.put("k", "v"); return tags; }`
2. `MalExpr_0` — implements `MalExpression` with field `_closure0`, method body calls `metric.tag(this._closure0)`

## ExpressionMetadata (replaces ExpressionParsingContext)

Metadata is extracted statically from the AST at compile time by `MALClassGenerator.extractMetadata()`. No ThreadLocal, no dry-run execution. The `Analyzer` calls `expression.metadata()` to get sample names, scope type, aggregation labels, downsampling, histogram/percentile info.

## Dependencies

All within this module (grammar, compiler, and runtime are merged):
- ANTLR4 grammar → generates lexer/parser at build time
- `MalExpression`, `ExpressionMetadata`, `SampleFamily` — in `dsl` package of this module
- `javassist` — bytecode generation
