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
| Generated classes | `...hierarchy.rule.rt.{yamlName}_L{lineNo}_{ruleName}` |
| Package holder | `...hierarchy.rule.rt.HierarchyRulePackageHolder` |
| SPI provider | `org.apache.skywalking.oap.server.core.config.v2.compiler.CompiledHierarchyRuleProvider` |
| Service type | `org.apache.skywalking.oap.server.core.query.type.Service` (in server-core) |

Class names are built from `yamlSource` (file name + line number) and `classNameHint` (rule name).
Example: `hierarchy_definition_L88_name` (rule `name` at line 88 of `hierarchy-definition.yml`).
Falls back to `HierarchyRule_<N>` (global counter) when no hint is set.

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

## Hierarchy Input Data Mock Principles

Hierarchy rules are simpler than MAL/LAL — they take two `Service` objects and return a boolean. The test data is implicit in the test code rather than YAML files.

### Principles

1. **Service objects are the input**: Each rule receives `(Service upper, Service lower)` and returns whether they match. Test data is built programmatically with `Service.builder().name("...").shortName("...").build()`.
2. **Four rule patterns**: See "Rule Patterns" above. Tests cover all four with various `name`/`shortName` combinations.
3. **v1-v2 comparison**: The checker test (`HierarchyComparisonTest` in `mal-lal-v1-v2-checker`) compiles each rule with both v1 (Groovy) and v2 (ANTLR4), runs them on the same `Service` pairs, and asserts identical results.
4. **No `.data.yaml` files**: Hierarchy rules are purely functional (two inputs → boolean), so mock data is inline in tests.

## Debug Output

When `SW_DYNAMIC_CLASS_ENGINE_DEBUG=true` environment variable is set, generated `.class` files are written to disk for inspection:

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
