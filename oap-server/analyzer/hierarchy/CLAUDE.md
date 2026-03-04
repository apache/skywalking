# Hierarchy Rule Compiler

Compiles hierarchy matching rule expressions into `BiFunction<Service, Service, Boolean>` implementation classes at runtime using ANTLR4 parsing and Javassist bytecode generation.

## Compilation Workflow

```
Rule expression string  (e.g., "{ (u, l) -> u.name == l.name }")
  → HierarchyRuleScriptParser.parse(expression)   [ANTLR4 lexer/parser → visitor]
  → HierarchyRuleModel (immutable AST)
  → HierarchyRuleClassGenerator.compile(ruleName, expression)
      1. classPool.makeClass()         — create class implementing BiFunction
      2. generateApplyMethod(model)    — emit Java source for apply(Object, Object)
      3. ctClass.toClass(HierarchyRulePackageHolder.class) — load via package anchor
  → BiFunction<Service, Service, Boolean> instance
```

The generated class implements:
```java
Object apply(Object arg0, Object arg1)
  // cast internally to Service and returns Boolean
```

No separate consumer/closure classes are needed — hierarchy rules are simple enough to compile into a single method body.

## File Structure

```
oap-server/analyzer/hierarchy/
  src/main/antlr4/.../HierarchyRuleLexer.g4     — ANTLR4 lexer grammar
  src/main/antlr4/.../HierarchyRuleParser.g4    — ANTLR4 parser grammar

  src/main/java/.../compiler/
    HierarchyRuleScriptParser.java              — ANTLR4 facade: expression → AST
    HierarchyRuleModel.java                     — Immutable AST model classes
    HierarchyRuleClassGenerator.java            — Javassist code generator
    CompiledHierarchyRuleProvider.java          — SPI provider: compiles rule expressions
    hierarchy/rule/rt/
      HierarchyRulePackageHolder.java           — Class loading anchor (empty marker)

  src/main/resources/META-INF/services/
    ...HierarchyDefinitionService$HierarchyRuleProvider — SPI registration

  src/test/java/.../compiler/
    HierarchyRuleScriptParserTest.java          — 5 parser tests
    HierarchyRuleClassGeneratorTest.java        — 4 generator tests
```

## Package & Class Naming

| Component | Package / Name |
|-----------|---------------|
| Parser/Model/Generator | `org.apache.skywalking.oap.server.core.config.v2.compiler` |
| Generated classes | `org.apache.skywalking.oap.server.core.config.v2.compiler.hierarchy.rule.rt.HierarchyRule_<N>` |
| Package holder | `org.apache.skywalking.oap.server.core.config.v2.compiler.hierarchy.rule.rt.HierarchyRulePackageHolder` |
| SPI provider | `org.apache.skywalking.oap.server.core.config.v2.compiler.CompiledHierarchyRuleProvider` |
| Service type | `org.apache.skywalking.oap.server.core.query.type.Service` (in server-core) |

`<N>` is a global `AtomicInteger` counter.

## Code Generation Details

**Field access mapping**: Property access in expressions maps to getter methods:
- `u.name` → `u.getName()`
- `l.shortName` → `l.getShortName()`
- Generic: `x.foo` → `x.getFoo()`

**Comparison operators**: `==` and `!=` use `java.util.Objects.equals()`. Numeric comparisons (`>`, `<`, `>=`, `<=`) emit direct operators.

**Method chains**: `l.shortName.substring(0, l.shortName.lastIndexOf("."))` generates chained Java method calls directly.

## Example

**Input**: `{ (u, l) -> u.name == l.name }`

**Generated `apply()` method**:
```java
public Object apply(Object arg0, Object arg1) {
  Service u = (Service) arg0;
  Service l = (Service) arg1;
  return Boolean.valueOf(java.util.Objects.equals(u.getName(), l.getName()));
}
```

**Input with block body**: `{ (u, l) -> { if (l.shortName.lastIndexOf(".") > 0) { return u.name == l.shortName.substring(0, l.shortName.lastIndexOf(".")); } return false; } }`

**Generated `apply()` method**:
```java
public Object apply(Object arg0, Object arg1) {
  Service u = (Service) arg0;
  Service l = (Service) arg1;
  if (l.getShortName().lastIndexOf(".") > 0) {
    return Boolean.valueOf(
        java.util.Objects.equals(
            u.getName(),
            l.getShortName().substring(0, l.getShortName().lastIndexOf("."))));
  }
  return Boolean.valueOf(false);
}
```

## Rule Patterns

Four rule types are defined in `hierarchy-definition.yml`:

| Rule Name | Expression Pattern |
|-----------|-------------------|
| `name` | `{ (u, l) -> u.name == l.name }` |
| `short-name` | `{ (u, l) -> u.shortName == l.shortName }` |
| `lower-short-name-remove-namespace` | `{ (u, l) -> { if (l.shortName.lastIndexOf(".") > 0) { return u.name == l.shortName.substring(0, l.shortName.lastIndexOf(".")); } return false; } }` |
| `lower-short-name-with-fqdn` | `{ (u, l) -> u.shortName == l.shortName.concat("." + u.shortName) }` |

## Debug Output

When `SW_OAL_ENGINE_DEBUG=true` environment variable is set, generated `.class` files are written to disk for inspection:

```
{skywalking}/hierarchy-rt/
  *.class          - Generated HierarchyRule .class files
```

This is the same env variable used by OAL. Useful for debugging code generation issues. In tests, use `setClassOutputDir(dir)` instead.

## Dependencies

Grammar, compiler, and runtime are merged into this module:
- ANTLR4 grammar → generates lexer/parser at build time
- `server-core` — `Service` type
- `javassist` — bytecode generation
