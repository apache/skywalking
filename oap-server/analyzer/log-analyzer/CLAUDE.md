# LAL Compiler

Compiles LAL (Log Analysis Language) scripts into `LalExpression` implementation classes at runtime using ANTLR4 parsing and Javassist bytecode generation.

## Compilation Workflow

```
LAL DSL string
  → LALScriptParser.parse(dsl)                 [ANTLR4 lexer/parser → listener]
  → LALScriptModel (immutable AST)
  → LALClassGenerator.compileFromModel(model)
      Phase 1: collectConsumers(model)   — pre-scan for blocks needing Consumer callbacks
      Phase 2: compileConsumerClass()    — generate each consumer as separate Javassist class
      Phase 3: classPool.makeClass()     — create main class implementing LalExpression
      Phase 4: generateExecuteMethod()   — emit Java source referencing this._consumerN fields
      Phase 5: ctClass.toClass(LalExpressionPackageHolder.class) + wire consumer fields
  → LalExpression instance
```

The generated class implements:
```java
void execute(Object filterSpec, Object binding)
  // cast internally to FilterSpec and Binding
```

## File Structure

```
oap-server/analyzer/log-analyzer/
  src/main/antlr4/.../LALLexer.g4       — ANTLR4 lexer grammar
  src/main/antlr4/.../LALParser.g4      — ANTLR4 parser grammar

  src/main/java/.../compiler/
    LALScriptParser.java                — ANTLR4 facade: DSL string → AST
    LALScriptModel.java                 — Immutable AST model classes
    LALClassGenerator.java              — Javassist code generator
    rt/
      LalExpressionPackageHolder.java   — Class loading anchor (empty marker)
      BindingAware.java                 — Interface for consumers needing Binding access

  src/test/java/.../compiler/
    LALScriptParserTest.java            — 8 parser tests
    LALClassGeneratorTest.java          — 6 generator tests
```

## Package & Class Naming

| Component | Package / Name |
|-----------|---------------|
| Parser/Model/Generator | `org.apache.skywalking.oap.log.analyzer.compiler` |
| Generated classes | `org.apache.skywalking.oap.log.analyzer.compiler.rt.LalExpr_<N>` |
| Consumer classes | `org.apache.skywalking.oap.log.analyzer.compiler.rt.LalExpr_<N>_C<M>` |
| Package holder | `org.apache.skywalking.oap.log.analyzer.compiler.rt.LalExpressionPackageHolder` |
| Binding aware | `org.apache.skywalking.oap.log.analyzer.compiler.rt.BindingAware` |
| Functional interface | `org.apache.skywalking.oap.log.analyzer.dsl.LalExpression` (in log-analyzer) |

`<N>` is a global `AtomicInteger` counter. `<M>` is the consumer index within the script.

## Consumer Pattern (BindingAware)

LAL's FilterSpec API uses `Consumer` callbacks: `filterSpec.extractor(Consumer<ExtractorSpec>)`, `filterSpec.sink(Consumer<SinkSpec>)`, etc. Since Javassist cannot compile anonymous inner classes, consumers are pre-compiled as separate classes.

Each consumer class implements both `java.util.function.Consumer` and `BindingAware`:
- `BindingAware.setBinding(Binding)` — called before each FilterSpec method to inject the current Binding
- `Consumer.accept(Object)` — casts to the specific Spec type and executes the block body

The main class's `execute()` method emits:
```java
((BindingAware) this._consumer0).setBinding(binding);
filterSpec.extractor(this._consumer0);
```

Consumer traversal order in `collectConsumers()` must exactly match the order in `generateFilterStatement()`.

## Javassist Constraints

- **No anonymous inner classes**: All `Consumer` callbacks pre-compiled as separate `CtClass` instances.
- **No lambda expressions**: Same workaround as above.
- **Spec class packages**: Parser specs are in `dsl.spec.parser.*` (not `dsl.spec.extractor.*`):
  - `spec.parser.TextParserSpec`, `spec.parser.JsonParserSpec`, `spec.parser.YamlParserSpec`
  - `spec.extractor.ExtractorSpec`
  - `spec.extractor.slowsql.SlowSqlSpec`, `spec.extractor.sampledtrace.SampledTraceSpec`
  - `spec.sink.SinkSpec`, `spec.sink.SamplerSpec`

## Example

**Input**: `filter { json {} extractor { service parsed.service as String } sink {} }`

Three classes are generated:

1. **`LalExpr_0_C0`** — Consumer for extractor block:
   ```java
   // implements Consumer, BindingAware
   public void accept(Object arg) {
     ExtractorSpec _t = (ExtractorSpec) arg;
     _t.service(String.valueOf(getAt(binding.parsed(), "service")));
   }
   ```

2. **`LalExpr_0`** — Main class implementing `LalExpression`:
   ```java
   public Consumer _consumer0;  // wired after toClass()

   public void execute(Object arg0, Object arg1) {
     FilterSpec filterSpec = (FilterSpec) arg0;
     Binding binding = (Binding) arg1;
     filterSpec.json();
     ((BindingAware) this._consumer0).setBinding(binding);
     filterSpec.extractor(this._consumer0);
     filterSpec.sink();
   }
   ```

**Consumer allocation rules**:
- `json {}` with no `abortOnFailure` → no consumer, emits `filterSpec.json()`
- `json { abortOnFailure }` → allocates a consumer
- `text { regexp '...' }` → allocates a consumer
- `text {}` with no regexp → no consumer, emits `filterSpec.text()`
- `extractor { ... }` → always allocates a consumer
- `sink {}` empty → no consumer, emits `filterSpec.sink()`
- `sink { enforcer {} }` → allocates a consumer

## Dependencies

All within this module (grammar, compiler, and runtime are merged):
- ANTLR4 grammar → generates lexer/parser at build time
- `LalExpression`, `Binding`, `FilterSpec`, all Spec classes — in `dsl` package of this module
- `javassist` — bytecode generation
