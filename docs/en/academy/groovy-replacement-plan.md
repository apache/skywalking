# Groovy Replacement Plan: Build-Time Transpiler for MAL, LAL, and Hierarchy Scripts

Reference: [Discussion #13716](https://github.com/apache/skywalking/discussions/13716)
Reference Implementation: [skywalking-graalvm-distro](https://github.com/apache/skywalking-graalvm-distro)

## 1. Background and Motivation

SkyWalking OAP server currently uses Groovy as the runtime scripting engine for three subsystems:

| Subsystem | YAML Files | Expressions | Groovy Pattern |
|-----------|-----------|-------------|----------------|
| MAL (Meter Analysis Language) | 71 (11 meter-analyzer-config, 55 otel-rules, 2 log-mal-rules, 2 envoy-metrics-rules, 1 telegraf-rules) | 1,254 metric + 29 filter | Dynamic Groovy: `propertyMissing()`, `ExpandoMetaClass` on `Number`, closures |
| LAL (Log Analysis Language) | 8 | 10 rules | `@CompileStatic` Groovy: delegation-based closure DSL, safe navigation (`?.`), `as` casts |
| Hierarchy Matching | 1 (hierarchy-definition.yml) | 4 rules | `GroovyShell.evaluate()` for `Closure<Boolean>` |

### Problems with Groovy Runtime

1. **Startup Cost**: 1,250+ `GroovyShell.parse()` calls at OAP boot, each spinning up the full Groovy compiler pipeline.
2. **Runtime Errors Instead of Compile-Time Errors**: MAL uses dynamic Groovy -- typos in metric names or invalid method chains are only discovered when that specific expression runs with real data.
3. **Debugging Complexity**: Stack traces include Groovy MOP internals (`CallSite`, `MetaClassImpl`, `ExpandoMetaClass`), obscuring the actual expression logic.
4. **Runtime Execution Performance (Most Critical)**: MAL expressions execute on every metrics ingestion cycle. Per-expression overhead from dynamic Groovy compounds at scale:
   - Property resolution: `CallSite` -> `MetaClassImpl.invokePropertyOrMissing()` -> `ExpressionDelegate.propertyMissing()` -> `ThreadLocal<Map>` lookup (4+ layers of indirection per metric name lookup)
   - Method calls: Groovy `CallSite` dispatch with MetaClass lookup and MOP interception checks
   - Arithmetic (`metric * 1000`): `ExpandoMetaClass` closure allocation + metaclass lookup + dynamic dispatch for what Java does as a single `imul`
   - Per ingestion cycle: ~1,250 `propertyMissing()` calls, ~3,750 MOP method dispatches, ~29 metaclass arithmetic ops, ~200 closure allocations
   - JIT cannot optimize Groovy's megamorphic call sites, defeating inlining and branch prediction
5. **GraalVM Incompatibility**: `invokedynamic` bootstrapping and `ExpandoMetaClass` are fundamentally incompatible with AOT compilation.

### Goal

Eliminate Groovy from the OAP runtime entirely. Groovy becomes a **build-time-only** dependency used solely for AST parsing by the transpiler. Zero `GroovyShell`, zero `ExpandoMetaClass`, zero MOP at runtime.

---

## 2. Solution Architecture

### Build-Time Transpiler: Groovy DSL -> Pure Java Source Code

```
BUILD TIME (Maven compile phase):
  MAL YAML files (71 files, 1,250+ expressions)
  LAL YAML files (8 files, 10 scripts)
          |
          v
  MalToJavaTranspiler / LalToJavaTranspiler
  (Groovy CompilationUnit at CONVERSION phase -- AST parsing only, no execution)
          |
          v
  ~1,254 MalExpr_*.java + ~6 LalExpr_*.java + MalFilter_*.java
          |
          v
  javax.tools.JavaCompiler -> .class files on classpath
  META-INF/mal-expressions.txt (manifest)
  META-INF/mal-filter-expressions.properties (manifest)
  META-INF/lal-expressions.txt (manifest)

RUNTIME (OAP Server):
  Class.forName(className) -> MalExpression / LalExpression instance
  Zero Groovy. Zero GroovyShell. Zero ExpandoMetaClass.
```

The transpiler approach is already fully implemented and validated in the [skywalking-graalvm-distro](https://github.com/apache/skywalking-graalvm-distro) repository.

---

## 3. Detailed Design

### 3.1 New Functional Interfaces

Three core interfaces replace Groovy's `DelegatingScript` and `Closure`:

```java
// MAL: replaces DelegatingScript + ExpandoMetaClass + ExpressionDelegate.propertyMissing()
@FunctionalInterface
public interface MalExpression {
    SampleFamily run(Map<String, SampleFamily> samples);
}

// MAL: replaces Closure<Boolean> from GroovyShell.evaluate() for filter expressions
@FunctionalInterface
public interface MalFilter {
    boolean test(Map<String, String> tags);
}

// LAL: replaces LALDelegatingScript + @CompileStatic closure DSL
@FunctionalInterface
public interface LalExpression {
    void execute(FilterSpec filterSpec, Binding binding);
}
```

### 3.2 SampleFamily: Closure -> Functional Interface

Five `SampleFamily` methods currently accept `groovy.lang.Closure`. Each gets a new overload with a Java functional interface. During transition both overloads coexist; eventually the Closure overloads are removed.

| Method | Current (Groovy) | New (Java) | Functional Interface |
|--------|-----------------|------------|---------------------|
| `tag()` | `Closure<?>` with `tags.key = val` | `TagFunction` | `Function<Map<String,String>, Map<String,String>>` |
| `filter()` | `Closure<Boolean>` with `tags.x == 'y'` | `SampleFilter` | `Predicate<Map<String,String>>` |
| `forEach()` | `Closure<Void>` with `(prefix, tags) -> ...` | `ForEachFunction` | `BiConsumer<String, Map<String,String>>` |
| `decorate()` | `Closure<Void>` with `entity -> ...` | `DecorateFunction` | `Consumer<MeterEntity>` |
| `instance(..., closure)` | `Closure<?>` with `tags -> Map.of(...)` | `PropertiesExtractor` | `Function<Map<String,String>, Map<String,String>>` |

Source location in upstream: `oap-server/analyzer/meter-analyzer/src/main/java/org/apache/skywalking/oap/meter/analyzer/dsl/SampleFamily.java`

### 3.3 MAL Transpiler: AST Mapping Rules

The `MalToJavaTranspiler` (~1,230 lines) parses each MAL expression string into a Groovy AST at `Phases.CONVERSION` (no code execution), then walks the AST to emit equivalent Java code.

#### Expression Mappings

| Groovy Construct | Java Output | Notes |
|-----------------|-------------|-------|
| `metric_name` (bare property) | `samples.getOrDefault("metric_name", SampleFamily.EMPTY)` | Replaces `propertyMissing()` dispatch |
| `.sum(['a','b'])` | `.sum(List.of("a", "b"))` | Direct method call |
| `.tagEqual('resource', 'cpu')` | `.tagEqual("resource", "cpu")` | Direct method call |
| `100 * metric` | `metric.multiply(100)` | Commutative: operands swapped |
| `100 - metric` | `metric.minus(100).negative()` | Non-commutative: negate |
| `100 / metric` | `metric.newValue(v -> 100 / v)` | Non-commutative: newValue |
| `metricA / metricB` | `metricA.div(metricB)` | SampleFamily-SampleFamily op |
| `.tag({tags -> tags.cluster = ...})` | `.tag(tags -> { tags.put("cluster", ...); return tags; })` | Closure -> lambda |
| `.filter({tags -> tags.job_name in [...]})` | `.filter(tags -> "...".equals(tags.get("job_name")))` | Closure -> predicate |
| `.forEach(['a','b'], {p, tags -> ...})` | `.forEach(List.of("a","b"), (p, tags) -> { ... })` | Closure -> BiConsumer |
| `.decorate({entity -> ...})` | `.decorate(entity -> { ... })` | Closure -> Consumer |
| `.instance(..., {tags -> Map.of(...)})` | `.instance(..., tags -> Map.of(...))` | Closure -> Function |
| `Layer.K8S` | `Layer.K8S` | Enum constant, passed through |
| `time()` | `Instant.now().getEpochSecond()` | Direct Java API |
| `AVG`, `SUM`, etc. | `DownsamplingType.AVG`, etc. | Enum constant reference |

#### Closure Body Translation

Inside closures, Groovy property-style access is mapped to explicit `Map` operations:

| Groovy Closure Pattern | Java Lambda Output |
|----------------------|-------------------|
| `tags.key = "value"` | `tags.put("key", "value")` |
| `tags.key` (read) | `tags.get("key")` |
| `tags.remove("key")` | `tags.remove("key")` |
| `tags.key == "value"` | `"value".equals(tags.get("key"))` |
| `tags.key != "value"` | `!"value".equals(tags.get("key"))` |
| `tags.key in ["a","b"]` | `List.of("a","b").contains(tags.get("key"))` |
| `if/else` in closure | `if/else` in lambda |
| `entity.serviceId = val` | `entity.setServiceId(val)` |

#### Filter Expression Mappings

Filter expressions (`filter: "{ tags -> ... }"`) generate `MalFilter` implementations:

| Groovy Filter | Java Output |
|--------------|-------------|
| `tags.job_name == 'mysql'` | `"mysql".equals(tags.get("job_name"))` |
| `tags.job_name != 'test'` | `!"test".equals(tags.get("job_name"))` |
| `tags.job_name in ['a','b']` | `List.of("a","b").contains(tags.get("job_name"))` |
| `cond1 && cond2` | `cond1 && cond2` |
| `cond1 \|\| cond2` | `cond1 \|\| cond2` |
| `!cond` | `!cond` |
| `tags.job_name` (truthiness) | `tags.get("job_name") != null` |

### 3.4 LAL Transpiler: AST Mapping Rules

The `LalToJavaTranspiler` (~950 lines) handles LAL's `@CompileStatic` delegation-based DSL. LAL scripts have a fundamentally different structure from MAL -- they are statement-based builder patterns rather than expression-based computations.

#### Statement Mappings

| Groovy Construct | Java Output |
|-----------------|-------------|
| `filter { ... }` | Body unwrapped, emitted directly on `filterSpec` |
| `json {}` | `filterSpec.json()` |
| `json { abortOnFailure false }` | `filterSpec.json(jp -> { jp.abortOnFailure(false); })` |
| `text { regexp /pattern/ }` | `filterSpec.text(tp -> { tp.regexp("pattern"); })` |
| `yaml {}` | `filterSpec.yaml()` |
| `extractor { ... }` | `filterSpec.extractor(ext -> { ... })` |
| `sink { ... }` | `filterSpec.sink(s -> { ... })` |
| `abort {}` | `filterSpec.abort()` |
| `service parsed.service as String` | `ext.service(String.valueOf(getAt(binding.parsed(), "service")))` |
| `layer parsed.layer as String` | `ext.layer(String.valueOf(getAt(binding.parsed(), "layer")))` |
| `tag(key: val)` | `ext.tag(Map.of("key", val))` |
| `timestamp parsed.time as String` | `ext.timestamp(String.valueOf(getAt(binding.parsed(), "time")))` |

#### Property Access and Safe Navigation

| Groovy Pattern | Java Output |
|---------------|-------------|
| `parsed.field` | `getAt(binding.parsed(), "field")` |
| `parsed.field.nested` | `getAt(getAt(binding.parsed(), "field"), "nested")` |
| `parsed?.field?.nested` | `((__v0 = binding.parsed()) == null ? null : ((__v1 = getAt(__v0, "field")) == null ? null : getAt(__v1, "nested")))` |
| `log.tags` | `binding.log().getTags()` |

#### Cast and Type Handling

| Groovy Pattern | Java Output |
|---------------|-------------|
| `expr as String` | `String.valueOf(expr)` |
| `expr as Long` | `toLong(expr)` |
| `expr as Integer` | `toInt(expr)` |
| `expr as Boolean` | `toBoolean(expr)` |
| `"${expr}"` (GString) | `"" + expr` |

#### LAL Spec Consumer Overloads

LAL spec classes (`FilterSpec`, `ExtractorSpec`, `SinkSpec`) get additional method overloads accepting `java.util.function.Consumer` alongside existing Groovy `Closure` parameters:

```java
// FilterSpec - existing
public void extractor(Closure<?> cl) { ... }
// FilterSpec - new overload
public void extractor(Consumer<ExtractorSpec> consumer) { ... }

// SinkSpec - existing
public void sampler(Closure<?> cl) { ... }
// SinkSpec - new overload
public void sampler(Consumer<SamplerSpec> consumer) { ... }
```

Methods requiring Consumer overloads: `text()`, `json()`, `yaml()`, `extractor()`, `sink()`, `slowSql()`, `sampledTrace()`, `metrics()`, `sampler()`, `enforcer()`, `dropper()`.

#### SHA-256 Deduplication

LAL manifest is keyed by SHA-256 hash of the DSL content. Identical scripts across different YAML files share one compiled class. In practice, 10 LAL rules map to 6 unique classes.

### 3.5 Hierarchy Script: v1/v2 Module Split

The hierarchy matching rules in `hierarchy-definition.yml` use `GroovyShell.evaluate()` to compile 4 Groovy closures at runtime. Unlike MAL/LAL, hierarchy does not need a transpiler (only 4 rules, finite set), but it follows the same v1/v2/checker module pattern for consistency and to remove Groovy from `server-core`.

#### Current State (server-core, Groovy-coupled)

`HierarchyDefinitionService.java` lives in `server-core` and is registered as a `Service` in `CoreModule`. Its inner class `MatchingRule` holds a Groovy `Closure<Boolean>`:

```java
// server-core/...config/HierarchyDefinitionService.java (current)
public static class MatchingRule {
    private final String name;
    private final String expression;
    private final Closure<Boolean> closure;       // groovy.lang.Closure

    public MatchingRule(final String name, final String expression) {
        GroovyShell sh = new GroovyShell();
        closure = (Closure<Boolean>) sh.evaluate(expression);  // Groovy at runtime
    }
}
```

This `MatchingRule` is referenced by three classes in `server-core`:
- `HierarchyDefinitionService` -- builds the rule map from YAML
- `HierarchyService` -- calls `matchingRule.getClosure().call(service, comparedService)` for auto-matching
- `HierarchyQueryService` -- reads the hierarchy definition map

#### Step 1: Make server-core Groovy-Free

Refactor `MatchingRule` in `server-core` to use a Java functional interface instead of `Closure<Boolean>`:

```java
// server-core/...config/HierarchyDefinitionService.java (refactored)
public static class MatchingRule {
    private final String name;
    private final String expression;
    private final BiFunction<Service, Service, Boolean> matcher;  // pure Java

    public MatchingRule(final String name, final String expression,
                        final BiFunction<Service, Service, Boolean> matcher) {
        this.name = name;
        this.expression = expression;
        this.matcher = matcher;
    }

    public boolean match(Service upper, Service lower) {
        return matcher.apply(upper, lower);
    }
}
```

`HierarchyDefinitionService.init()` no longer compiles Groovy expressions itself. Instead, it receives a `Map<String, BiFunction<Service, Service, Boolean>>` (the rule registry) from outside -- injected by whichever implementation module (v1 or v2) is active.

`HierarchyService` changes from `matchingRule.getClosure().call(u, l)` to `matchingRule.match(u, l)`.

Remove all `groovy.lang.*` imports from `server-core`.

#### Step 2: hierarchy-v1 (Groovy-based, for checker only)

```java
// analyzer/hierarchy-v1/.../GroovyHierarchyRuleProvider.java
public class GroovyHierarchyRuleProvider {
    public static Map<String, BiFunction<Service, Service, Boolean>> buildRules(
            Map<String, String> ruleExpressions) {
        Map<String, BiFunction<Service, Service, Boolean>> rules = new HashMap<>();
        GroovyShell sh = new GroovyShell();
        ruleExpressions.forEach((name, expression) -> {
            Closure<Boolean> closure = (Closure<Boolean>) sh.evaluate(expression);
            rules.put(name, (u, l) -> closure.call(u, l));
        });
        return rules;
    }
}
```

This module depends on Groovy and wraps the original `GroovyShell.evaluate()` logic. It is NOT included in the runtime classpath -- only used by the checker.

#### Step 3: hierarchy-v2 (Pure Java, for runtime)

```java
// analyzer/hierarchy-v2/.../JavaHierarchyRuleProvider.java
public class JavaHierarchyRuleProvider {
    private static final Map<String, BiFunction<Service, Service, Boolean>> RULE_REGISTRY;
    static {
        RULE_REGISTRY = new HashMap<>();
        RULE_REGISTRY.put("name",
            (u, l) -> Objects.equals(u.getName(), l.getName()));
        RULE_REGISTRY.put("short-name",
            (u, l) -> Objects.equals(u.getShortName(), l.getShortName()));
        RULE_REGISTRY.put("lower-short-name-remove-ns", (u, l) -> {
            String sn = l.getShortName();
            int dot = sn.lastIndexOf('.');
            return dot > 0 && Objects.equals(u.getShortName(), sn.substring(0, dot));
        });
        RULE_REGISTRY.put("lower-short-name-with-fqdn", (u, l) -> {
            String sn = u.getShortName();
            int colon = sn.lastIndexOf(':');
            return colon > 0 && Objects.equals(
                sn.substring(0, colon),
                l.getShortName() + ".svc.cluster.local");
        });
    }

    public static Map<String, BiFunction<Service, Service, Boolean>> buildRules(
            Map<String, String> ruleExpressions) {
        Map<String, BiFunction<Service, Service, Boolean>> rules = new HashMap<>();
        ruleExpressions.forEach((name, expression) -> {
            BiFunction<Service, Service, Boolean> fn = RULE_REGISTRY.get(name);
            if (fn == null) {
                throw new IllegalArgumentException(
                    "Unknown hierarchy matching rule: " + name
                    + ". Known rules: " + RULE_REGISTRY.keySet());
            }
            rules.put(name, fn);
        });
        return rules;
    }
}
```

Unknown rule names fail fast at startup with `IllegalArgumentException`. The YAML file (`hierarchy-definition.yml`) continues to reference rule names (`name`, `short-name`, etc.) -- the Groovy expression strings in `auto-matching-rules` become documentation-only at runtime.

#### Step 4: hierarchy-v1-v2-checker

```java
// analyzer/hierarchy-v1-v2-checker/.../HierarchyRuleComparisonTest.java
class HierarchyRuleComparisonTest {
    // Load rule expressions from hierarchy-definition.yml
    // For each rule:
    //   Path A: GroovyHierarchyRuleProvider.buildRules() (v1)
    //   Path B: JavaHierarchyRuleProvider.buildRules() (v2)
    //   Construct test Service pairs (matching and non-matching cases)
    //   Assert v1.match(u, l) == v2.match(u, l) for all test pairs
}
```

Test cases cover all 4 rules with realistic service name patterns:
- `name`: exact match and mismatch
- `short-name`: exact shortName match and mismatch
- `lower-short-name-remove-ns`: `"svc" == "svc.namespace"` and edge cases (no dot, empty)
- `lower-short-name-with-fqdn`: `"db:3306"` vs `"db.svc.cluster.local"` and edge cases (no colon, wrong suffix)

---

## 4. Module Structure

### 4.1 Upstream Module Layout

```
oap-server/
  server-core/                       # MODIFIED: MatchingRule uses BiFunction (no Groovy imports)

  analyzer/
    meter-analyzer/                  # Modified: add MalExpression, functional interfaces
    log-analyzer/                    # Modified: add LalExpression, Consumer overloads

    mal-lal-v1/                      # NEW: Move existing Groovy-based code here
      meter-analyzer-v1/             # Original MAL (GroovyShell + ExpandoMetaClass)
      log-analyzer-v1/               # Original LAL (GroovyShell + @CompileStatic)

    mal-lal-v2/                      # NEW: Pure Java transpiler-based implementations
      meter-analyzer-v2/             # MalExpression loader + functional interface dispatch
      log-analyzer-v2/               # LalExpression loader + Consumer dispatch
      mal-transpiler/                # Build-time: Groovy AST -> Java source (MAL)
      lal-transpiler/                # Build-time: Groovy AST -> Java source (LAL)

    mal-lal-v1-v2-checker/           # NEW: Dual-path comparison tests (MAL + LAL)
      73 MAL test classes (1,281 assertions)
      5 LAL test classes (19 assertions)

    hierarchy-v1/                    # NEW: Groovy-based hierarchy rule provider (checker only)
    hierarchy-v2/                    # NEW: Pure Java hierarchy rule provider (runtime)
    hierarchy-v1-v2-checker/         # NEW: Dual-path comparison tests (hierarchy)
```

### 4.2 Dependency Graph

```
mal-transpiler ──────────────> groovy (build-time only, for AST parsing)
lal-transpiler ──────────────> groovy (build-time only, for AST parsing)
hierarchy-v1 ────────────────> groovy (checker only, not runtime)

meter-analyzer-v2 ──────────> meter-analyzer (interfaces + SampleFamily)
log-analyzer-v2 ────────────> log-analyzer (interfaces + spec classes)
hierarchy-v2 ───────────────> server-core (MatchingRule with BiFunction)

mal-lal-v1-v2-checker ──────> mal-lal-v1 (Groovy path)
mal-lal-v1-v2-checker ──────> mal-lal-v2 (Java path)
hierarchy-v1-v2-checker ────> hierarchy-v1 (Groovy path)
hierarchy-v1-v2-checker ────> hierarchy-v2 (Java path)

server-starter ─────────────> meter-analyzer-v2 (runtime, no Groovy)
server-starter ─────────────> log-analyzer-v2 (runtime, no Groovy)
server-starter ─────────────> hierarchy-v2 (runtime, no Groovy)
server-starter ────────────X─> mal-lal-v1 (NOT in runtime)
server-starter ────────────X─> hierarchy-v1 (NOT in runtime)
```

### 4.3 Key Design Principle: No Coexistence

v1 (Groovy) and v2 (Java) never coexist in the OAP runtime classpath. The `mal-lal-v1` and `hierarchy-v1` modules are only dependencies of their respective checker modules for CI validation. The runtime (`server-starter`) depends only on v2 modules.

---

## 5. Implementation Steps

### Phase 1: Interfaces and SampleFamily Modifications

**Files to modify:**

1. **Create `MalExpression.java`** in `meter-analyzer`
   - Path: `oap-server/analyzer/meter-analyzer/src/main/java/org/apache/skywalking/oap/meter/analyzer/dsl/MalExpression.java`

2. **Create `MalFilter.java`** in `meter-analyzer`
   - Path: `oap-server/analyzer/meter-analyzer/src/main/java/org/apache/skywalking/oap/meter/analyzer/dsl/MalFilter.java`

3. **Create functional interfaces** in `meter-analyzer`
   - `TagFunction extends Function<Map<String,String>, Map<String,String>>`
   - `SampleFilter extends Predicate<Map<String,String>>`
   - `ForEachFunction extends BiConsumer<String, Map<String,String>>`
   - `DecorateFunction extends Consumer<MeterEntity>`
   - `PropertiesExtractor extends Function<Map<String,String>, Map<String,String>>`

4. **Add overloads to `SampleFamily.java`**
   - Add `tag(TagFunction)` alongside existing `tag(Closure<?>)`
   - Add `filter(SampleFilter)` alongside existing `filter(Closure<Boolean>)`
   - Add `forEach(List, ForEachFunction)` alongside existing `forEach(List, Closure)`
   - Add `decorate(DecorateFunction)` alongside existing `decorate(Closure)`
   - Add `instance(..., PropertiesExtractor)` alongside existing `instance(..., Closure)`

5. **Create `LalExpression.java`** in `log-analyzer`
   - Path: `oap-server/analyzer/log-analyzer/src/main/java/org/apache/skywalking/oap/log/analyzer/dsl/LalExpression.java`

6. **Add Consumer overloads to LAL spec classes**
   - `FilterSpec`: `text(Consumer)`, `json(Consumer)`, `yaml(Consumer)`, `extractor(Consumer)`, `sink(Consumer)`, `filter(Consumer)`
   - `ExtractorSpec`: `slowSql(Consumer)`, `sampledTrace(Consumer)`, `metrics(Consumer)`
   - `SinkSpec`: `sampler(Consumer)`, `enforcer(Consumer)`, `dropper(Consumer)`

### Phase 2: MAL Transpiler

**New module: `oap-server/analyzer/mal-lal-v2/mal-transpiler/`**

1. **`MalToJavaTranspiler.java`** (~1,230 lines)
   - Uses `org.codehaus.groovy.control.CompilationUnit` at `Phases.CONVERSION`
   - Walks AST via recursive visitor pattern
   - Core methods:
     - `transpileExpression(String className, String expression)` -> generates Java source for `MalExpression`
     - `transpileFilter(String className, String filterLiteral)` -> generates Java source for `MalFilter`
     - `collectSampleNames(Expression expr)` -> extracts all metric name references
     - `visitExpression(Expression node)` -> recursive Java code emitter
     - `visitClosureExpression(ClosureExpression node, String contextType)` -> closure-to-lambda
     - `compileAll()` -> batch `javac` compile + manifest generation

2. **Maven integration**: `exec-maven-plugin` during `generate-sources` phase
   - Reads all MAL YAML files from resources
   - Generates Java source to `target/generated-sources/mal/`
   - Compiles to `target/classes/`
   - Writes `META-INF/mal-expressions.txt` and `META-INF/mal-filter-expressions.properties`

### Phase 3: LAL Transpiler

**New module: `oap-server/analyzer/mal-lal-v2/lal-transpiler/`**

1. **`LalToJavaTranspiler.java`** (~950 lines)
   - Same AST approach as MAL but statement-based emission
   - Core methods:
     - `transpile(String className, String dslText)` -> generates Java source for `LalExpression`
     - `emitStatement(Statement node, String receiver, BindingContext ctx)` -> statement emitter
     - `visitConditionExpr(Expression node)` -> boolean expression emitter
     - `emitPropertyAccess(PropertyExpression node)` -> `getAt()` with null safety
   - SHA-256 deduplication: identical DSL content shares one class
   - Helper methods in generated class: `getAt()`, `toLong()`, `toInt()`, `toBoolean()`, `isTruthy()`, `isNonEmptyString()`

2. **Maven integration**: same `exec-maven-plugin` approach
   - Writes `META-INF/lal-expressions.txt` (SHA-256 hash -> FQCN)

### Phase 4: Runtime Loading (v2 Modules)

**New module: `oap-server/analyzer/mal-lal-v2/meter-analyzer-v2/`**

1. **Modified `DSL.java`** (MAL runtime):
   ```java
   public static Expression parse(String metricName, String expression) {
       Map<String, String> manifest = loadManifest("META-INF/mal-expressions.txt");
       String className = manifest.get(metricName);
       MalExpression malExpr = (MalExpression) Class.forName(className)
           .getDeclaredConstructor().newInstance();
       return new Expression(metricName, expression, malExpr);
   }
   ```

2. **Modified `Expression.java`** (MAL runtime):
   - Wraps `MalExpression` instead of `DelegatingScript`
   - `run()` calls `malExpression.run(sampleFamilies)` directly
   - No `ExpandoMetaClass`, no `ExpressionDelegate`, no `ThreadLocal<Map>`

3. **Modified `FilterExpression.java`** (MAL runtime):
   - Loads `MalFilter` from `META-INF/mal-filter-expressions.properties`
   - `filter()` calls `malFilter::test` via `SampleFamily.filter(SampleFilter)`

**New module: `oap-server/analyzer/mal-lal-v2/log-analyzer-v2/`**

4. **Modified `DSL.java`** (LAL runtime):
   - Computes SHA-256 of DSL text, loads class from `META-INF/lal-expressions.txt`
   - `evaluate()` calls `lalExpression.execute(filterSpec, binding)` directly
   - No `GroovyShell`, no `LALDelegatingScript`

### Phase 5: Hierarchy v1/v2 Module Split

**Step 5a: Refactor `server-core` to remove Groovy**

**File to modify:** `oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/config/HierarchyDefinitionService.java`

1. Change `MatchingRule.closure` from `Closure<Boolean>` to `BiFunction<Service, Service, Boolean> matcher`
2. Add constructor that accepts the `BiFunction` matcher directly
3. Replace `getClosure()` with `match(Service upper, Service lower)` method
4. Change `init()` to accept a rule registry (`Map<String, BiFunction<Service, Service, Boolean>>`) from outside instead of calling `GroovyShell.evaluate()` internally
5. Remove all `groovy.lang.*` imports

**File to modify:** `oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/hierarchy/HierarchyService.java`

6. Change `matchingRule.getClosure().call(service, comparedService)` (lines 201-203, 220-222) to `matchingRule.match(service, comparedService)`

**Step 5b: Create hierarchy-v1 module**

**New module:** `oap-server/analyzer/hierarchy-v1/`

1. `GroovyHierarchyRuleProvider.java`: wraps original `GroovyShell.evaluate()` logic
2. Takes `Map<String, String>` (rule name -> Groovy expression) from YAML
3. Returns `Map<String, BiFunction<Service, Service, Boolean>>` by evaluating closures
4. Depends on Groovy -- NOT included in runtime, only used by checker

**Step 5c: Create hierarchy-v2 module**

**New module:** `oap-server/analyzer/hierarchy-v2/`

1. `JavaHierarchyRuleProvider.java`: static `RULE_REGISTRY` with 4 Java lambdas
2. Takes `Map<String, String>` (rule name -> Groovy expression) from YAML (expression ignored, only name used for registry lookup)
3. Returns `Map<String, BiFunction<Service, Service, Boolean>>` from the registry
4. Fails fast with `IllegalArgumentException` for unknown rule names
5. Zero Groovy dependency

### Phase 6: Comparison Test Suites

**New module: `oap-server/analyzer/mal-lal-v1-v2-checker/`**

1. **MAL comparison tests** (73 test classes):
   - Base class `MALScriptComparisonBase` runs dual-path comparison:
     - Path A: Fresh Groovy compilation with upstream `CompilerConfiguration`
     - Path B: Load transpiled `MalExpression` from manifest
   - Both receive identical `Map<String, SampleFamily>` input
   - Compare: `ExpressionParsingContext` (scope, function, datatype, downsampling), `SampleFamily` result (values, labels, entity descriptions)
   - JUnit 5 `@TestFactory` generates `DynamicTest` per metric rule
   - Test data must be non-trivial to prevent vacuous agreement (both returning empty/null)

2. **LAL comparison tests** (5 test classes):
   - Base class `LALScriptComparisonBase` runs dual-path comparison:
     - Path A: Groovy with `@CompileStatic` + `LALPrecompiledExtension`
     - Path B: Load `LalExpression` from manifest via SHA-256
   - Compare: `shouldAbort()`, `shouldSave()`, LogData.Builder state, metrics container, `databaseSlowStatement`, `sampledTraceBuilder`

3. **Test statistics**: 1,281 MAL assertions + 19 LAL assertions = 1,300 total

**New module: `oap-server/analyzer/hierarchy-v1-v2-checker/`**

4. **Hierarchy comparison tests**:
   - Load rule expressions from `hierarchy-definition.yml`
   - For each of the 4 rules:
     - Path A: `GroovyHierarchyRuleProvider.buildRules()` (v1, Groovy closures)
     - Path B: `JavaHierarchyRuleProvider.buildRules()` (v2, Java lambdas)
   - Construct test `Service` pairs covering matching and non-matching cases:
     - `name`: exact match `("svc", "svc")` -> true, `("svc", "other")` -> false
     - `short-name`: shortName match/mismatch
     - `lower-short-name-remove-ns`: `"svc"` vs `"svc.namespace"` -> true, no dot -> false, empty -> false
     - `lower-short-name-with-fqdn`: `"db:3306"` vs `"db.svc.cluster.local"` -> true, no colon -> false, wrong suffix -> false
   - Assert `v1.match(u, l) == v2.match(u, l)` for all test pairs

### Phase 7: Cleanup and Dependency Removal

1. **Move v1 code to `mal-lal-v1/` and `hierarchy-v1/`** (or mark as `<scope>test</scope>`)
2. **Remove Groovy from runtime classpath**: `groovy-5.0.3.jar` (~7 MB) becomes test-only
3. **Remove from `server-starter` dependencies**: replace v1 with v2 module references for MAL, LAL, and hierarchy
4. **Remove `NumberClosure.java`**: no longer needed without `ExpandoMetaClass`
5. **Remove `ExpressionDelegate.propertyMissing()`**: replaced by `samples.getOrDefault()`
6. **Remove Groovy closure overloads from `SampleFamily`** (after v1 is fully deprecated)
7. **Remove `LALDelegatingScript.java`**: replaced by `LalExpression` interface
8. **Verify `server-core` has zero Groovy imports**: `HierarchyDefinitionService` and `HierarchyService` now use `BiFunction` only

### Phase 8: Replace v2 Manifest Loading with Real Compilers (ANTLR4 + Javassist)

Phase 7 completed: v2 modules are standalone (zero Groovy), v1 depends on v2. Currently, v2 loads transpiled classes via manifest files (`META-INF/mal-expressions.txt`, `META-INF/lal-expressions.txt`) that were pre-compiled at build time. This prevents on-demand config changes since MAL/LAL/hierarchy configs are in the final package and users may want to modify them.

The goal is to replace this manifest-based approach with **real compilers** following the OAL pattern: ANTLR4 grammar -> parser -> model -> Javassist class generation -> listener notification. This enables runtime compilation when configs change.

#### Module Renaming: Drop `-v2` Suffix

Since v1 modules move to `test/script-compiler/`, the v2 modules become the primary ones and lose the `-v2` suffix:
- `meter-analyzer-v2` -> `meter-analyzer` (package stays `o.a.s.oap.meter.analyzer`)
- `log-analyzer-v2` -> `log-analyzer` (package stays `o.a.s.oap.log.analyzer`)
- `hierarchy-v2` -> `hierarchy` (no v1 name conflict since v1 moves out)
- `.v2.` sub-packages (`dsl.v2.DSL`, `dsl.v2.Binding`, etc.) merge back into parent packages (`dsl.DSL`, `dsl.Binding`)

#### Target Module Structure

```
oap-server/
  mal-grammar/                   NEW — ANTLR4 grammar for MAL expressions
  lal-grammar/                   NEW — ANTLR4 grammar for LAL scripts
  hierarchy-rule-grammar/        NEW — ANTLR4 grammar for hierarchy matching rules

oap-server/analyzer/
  agent-analyzer/                (stays)
  event-analyzer/                (stays)
  meter-analyzer/                (renamed from meter-analyzer-v2, runtime MAL, calls mal-compiler)
  log-analyzer/                  (renamed from log-analyzer-v2, runtime LAL, calls lal-compiler)
  hierarchy/                     (renamed from hierarchy-v2, calls hierarchy-rule-compiler)
  mal-compiler/                  NEW — MAL expression compiler engine
  lal-compiler/                  NEW — LAL script compiler engine
  hierarchy-rule-compiler/       NEW — hierarchy rule compiler engine

test/script-compiler/            NEW — aggregator for v1/transpiler/checker (not in dist)
  mal-groovy/                    <- meter-analyzer v1 (Groovy)
  lal-groovy/                    <- log-analyzer v1 (Groovy)
  hierarchy-groovy/              <- hierarchy-v1 (Groovy)
  mal-transpiler/                <- mal-transpiler
  lal-transpiler/                <- lal-transpiler
  mal-lal-v1-v2-checker/         <- mal-lal-v1-v2-checker
  hierarchy-v1-v2-checker/       <- hierarchy-v1-v2-checker
```

#### Generated Class Grouping by Config File Name

MAL metrics come from YAML config files (e.g., `oap.yaml`, `spring-micrometer.yaml`). Each file contains multiple `metricsRules`. The compiler groups generated classes by source file name.

- **MAL Compiler API**: `MALCompilerEngine.compile(configFileName, MetricRuleConfig)` -> grouped by file
- **Generated class naming**: `rt.<configFile>.MalExpr_<metricName>`, e.g., `rt.oap.MalExpr_instance_jvm_cpu`
- **LAL Compiler API**: `LALCompilerEngine.compile(configFileName, List<LALConfig>)` -> grouped by file
- **Generated class naming**: `rt.<configFile>.LalExpr_<ruleName>`

#### Eliminate `ExpressionParsingContext` ThreadLocal (MAL only)

The current MAL `run()` method serves dual purposes controlled by a ThreadLocal:
1. **Parse phase** (startup): `ExpressionParsingContext` ThreadLocal is set, `run()` is called with an empty map to discover which metric names the expression references
2. **Runtime phase** (every ingestion cycle): ThreadLocal is not set, `run()` computes the actual result

This is eliminated by extracting metadata statically. The `MalExpression` interface gains a `metadata()` method:

```java
public interface MalExpression {
    /** Pure computation. No side effects. */
    SampleFamily run(Map<String, SampleFamily> samples);

    /** Compile-time metadata -- sample names, scope, downsampling, etc. */
    ExpressionMetadata metadata();
}
```

The ANTLR4 compiler extracts all metadata from the parse tree at compile time:

| Metadata | Extracted from |
|--|--|
| `sampleNames` | Bare identifiers (metric references) |
| `scopeType` | Terminal method: `.service()`, `.instance()`, `.endpoint()` |
| `downsampling` | Aggregation arg: `AVG`, `SUM`, `MAX`, etc. |
| `percentiles` | `.percentile()` call arguments |
| `isHistogram` | Presence of `.histogram()` in chain |

Generated class emits metadata as static fields:

```java
public class MalExpr_instance_jvm_cpu implements MalExpression {
    private static final ExpressionMetadata METADATA = new ExpressionMetadata(
        List.of("instance_jvm_cpu"),  // sampleNames
        ScopeType.SERVICE_INSTANCE,   // from .service()/instance() call
        DownsamplingType.AVG          // from aggregation
    );

    @Override
    public ExpressionMetadata metadata() { return METADATA; }

    @Override
    public SampleFamily run(Map<String, SampleFamily> samples) {
        return ((SampleFamily) samples.getOrDefault("instance_jvm_cpu", SampleFamily.EMPTY))
            .sum(List.of("service", "instance"));
    }
}
```

Result: `run()` is pure computation, `metadata()` is static facts, `ExpressionParsingContext` and its ThreadLocal are deleted. No dry run with empty map at startup.

LAL and hierarchy do **not** have this problem -- LAL passes `Binding` explicitly as a parameter, hierarchy rules are stateless lambdas.

#### Implementation Sub-Phases

- 8.1: Rename modules (drop -v2 suffix, flatten .v2. sub-packages)
- 8.2: Grammar modules (ANTLR4 .g4 files)
- 8.3: Compiler model + parser (no code gen)
- 8.4: Javassist code generation (including static `ExpressionMetadata` on generated MAL classes)
- 8.5: Engine integration (wire compilers into renamed modules, delete `ExpressionParsingContext`)
- 8.6: Move v1/transpiler/checker to test/script-compiler/
- 8.7: Cleanup (remove manifests, verify zero Groovy)

---

## 6. What Gets Removed from Runtime

| Component | Current | After |
|-----------|---------|-------|
| `GroovyShell.parse()` in MAL `DSL.java` | 1,250+ calls at boot | `Class.forName()` from manifest |
| `GroovyShell.evaluate()` in MAL `FilterExpression.java` | 29 filter compilations | `Class.forName()` from manifest |
| `GroovyShell.parse()` in LAL `DSL.java` | 10 script compilations | `Class.forName()` from manifest |
| `GroovyShell.evaluate()` in `HierarchyDefinitionService` | 4 rule compilations | `hierarchy-v2` Java lambda registry |
| `Closure<Boolean>` in `MatchingRule` | Groovy closure in `server-core` | `BiFunction<Service, Service, Boolean>` (Groovy-free `server-core`) |
| `ExpandoMetaClass` registration in `Expression.empower()` | Runtime metaclass on `Number` | Direct `multiply()`/`div()` method calls |
| `ExpressionDelegate.propertyMissing()` | Dynamic property dispatch | `samples.getOrDefault()` |
| `groovy.lang.Closure` in `SampleFamily` | 5 method signatures | Java functional interfaces |
| `groovy-5.0.3.jar` runtime dependency | ~7 MB on classpath | Removed (build-time only) |

---

## 7. Transpiler Technical Details

### 7.1 AST Parsing Strategy

Both transpilers use Groovy's `CompilationUnit` at `Phases.CONVERSION`:

```java
CompilationUnit cu = new CompilationUnit();
cu.addSource("expression", new StringReaderSource(
    new StringReader(groovyCode), cu.getConfiguration()));
cu.compile(Phases.CONVERSION);  // Parse + AST transform, no codegen
ModuleNode ast = cu.getAST();
```

This extracts the complete syntax tree without:
- Generating Groovy bytecode
- Resolving classes on classpath
- Activating MOP or MetaClass

The Groovy dependency is therefore **build-time only**.

### 7.2 MAL Arithmetic Operand Swap

The transpiler must replicate the exact behavior of upstream's `ExpandoMetaClass` on `Number`. When a `Number` appears on the left side of an operator with a `SampleFamily` on the right, the operands must be handled carefully:

```
N + SF  ->  SF.plus(N)                    // commutative, swap operands
N - SF  ->  SF.minus(N).negative()        // non-commutative: (N - SF) = -(SF - N)
N * SF  ->  SF.multiply(N)               // commutative, swap operands
N / SF  ->  SF.newValue(v -> N / v)      // non-commutative: per-sample (N / sample_value)
```

The transpiler detects `Number` vs `SampleFamily` operand types by tracking whether a sub-expression references sample names (metric properties) or is a numeric literal/constant.

### 7.3 Batch Compilation

Generated Java sources are compiled in a single `javac` invocation via `javax.tools.JavaCompiler`:

```java
JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
List<JavaFileObject> sources = /* all generated .java files */;
compiler.getTask(null, fileManager, diagnostics, options, null, sources).call();
```

This avoids 1,250+ individual `javac` invocations and provides full cross-file type checking.

### 7.4 Manifest Format

**`META-INF/mal-expressions.txt`**:
```
metric_name_1=org.apache.skywalking.oap.meter.analyzer.dsl.generated.MalExpr_metric_name_1
metric_name_2=org.apache.skywalking.oap.meter.analyzer.dsl.generated.MalExpr_metric_name_2
...
```

**`META-INF/mal-filter-expressions.properties`**:
```
{ tags -> tags.job_name == 'mysql-monitoring' }=org.apache.skywalking.oap.meter.analyzer.dsl.generated.MalFilter_0a1b2c3d
...
```

**`META-INF/lal-expressions.txt`**:
```
sha256_hash_1=org.apache.skywalking.oap.log.analyzer.dsl.generated.LalExpr_sha256_hash_1
sha256_hash_2=org.apache.skywalking.oap.log.analyzer.dsl.generated.LalExpr_sha256_hash_2
...
```

---

## 8. Worked Examples

### 8.1 MAL Expression: K8s Node CPU Capacity

**Groovy (upstream):**
```groovy
kube_node_status_capacity.tagEqual('resource', 'cpu')
    .sum(['node'])
    .tag({tags -> tags.node_name = tags.node; tags.remove("node")})
    .service(['node_name'], Layer.K8S)
```

**Transpiled Java:**
```java
public class MalExpr_k8s_node_cpu implements MalExpression {
    @Override
    public SampleFamily run(Map<String, SampleFamily> samples) {
        return samples.getOrDefault("kube_node_status_capacity", SampleFamily.EMPTY)
            .tagEqual("resource", "cpu")
            .sum(List.of("node"))
            .tag(tags -> {
                tags.put("node_name", tags.get("node"));
                tags.remove("node");
                return tags;
            })
            .service(List.of("node_name"), Layer.K8S);
    }
}
```

### 8.2 MAL Expression: Arithmetic with Number on Left

**Groovy (upstream):**
```groovy
100 - container_cpu_usage / container_resource_limit_cpu * 100
```

**Transpiled Java:**
```java
public class MalExpr_cpu_percent implements MalExpression {
    @Override
    public SampleFamily run(Map<String, SampleFamily> samples) {
        return samples.getOrDefault("container_cpu_usage", SampleFamily.EMPTY)
            .div(samples.getOrDefault("container_resource_limit_cpu", SampleFamily.EMPTY))
            .multiply(100)
            .minus(100)
            .negative();
    }
}
```

### 8.3 MAL Filter Expression

**Groovy (upstream):**
```groovy
{ tags -> tags.job_name == 'mysql-monitoring' }
```

**Transpiled Java:**
```java
public class MalFilter_mysql implements MalFilter {
    @Override
    public boolean test(Map<String, String> tags) {
        return "mysql-monitoring".equals(tags.get("job_name"));
    }
}
```

### 8.4 LAL Expression: MySQL Slow SQL

**Groovy (upstream):**
```groovy
filter {
    json {}
    extractor {
        layer parsed.layer as String
        service parsed.service as String
        timestamp parsed.time as String
        if (tag("LOG_KIND") == "SLOW_SQL") {
            slowSql {
                id parsed.id as String
                statement parsed.statement as String
                latency parsed.query_time as Long
            }
        }
    }
    sink {}
}
```

**Transpiled Java:**
```java
public class LalExpr_mysql_slowsql implements LalExpression {

    @Override
    public void execute(FilterSpec filterSpec, Binding binding) {
        filterSpec.json();
        filterSpec.extractor(ext -> {
            ext.layer(String.valueOf(getAt(binding.parsed(), "layer")));
            ext.service(String.valueOf(getAt(binding.parsed(), "service")));
            ext.timestamp(String.valueOf(getAt(binding.parsed(), "time")));
            if ("SLOW_SQL".equals(ext.tag("LOG_KIND"))) {
                ext.slowSql(ss -> {
                    ss.id(String.valueOf(getAt(binding.parsed(), "id")));
                    ss.statement(String.valueOf(getAt(binding.parsed(), "statement")));
                    ss.latency(toLong(getAt(binding.parsed(), "query_time")));
                });
            }
        });
        filterSpec.sink(s -> {});
    }

    private static Object getAt(Object obj, String key) {
        if (obj instanceof Binding.Parsed) return ((Binding.Parsed) obj).getAt(key);
        if (obj instanceof Map) return ((Map<?, ?>) obj).get(key);
        return null;
    }

    private static long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return 0L;
    }
}
```

### 8.5 Hierarchy Rule: lower-short-name-remove-ns

**Groovy (upstream, in hierarchy-definition.yml):**
```groovy
{ (u, l) -> {
    if(l.shortName.lastIndexOf('.') > 0)
        return u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.'));
    return false;
} }
```

**Java replacement (in HierarchyDefinitionService.java):**
```java
RULE_REGISTRY.put("lower-short-name-remove-ns", (u, l) -> {
    String sn = l.getShortName();
    int dot = sn.lastIndexOf('.');
    return dot > 0 && Objects.equals(u.getShortName(), sn.substring(0, dot));
});
```

---

## 9. Verification Strategy

### 9.1 Dual-Path Comparison Testing

Every generated Java class is validated against the original Groovy behavior in CI:

```
For each MAL YAML file:
  For each metric rule:
    1. Compile expression with Groovy (v1 path)
    2. Load transpiled MalExpression (v2 path)
    3. Construct realistic sample data (non-trivial to prevent vacuous agreement)
    4. Run both paths with identical input
    5. Assert identical output:
       - ExpressionParsingContext (scope, function, datatype, samples, downsampling)
       - SampleFamily result (values, labels, entity descriptions)
```

### 9.2 Staleness Detection

Properties files record SHA-256 hashes of upstream classes that have same-FQCN replacements. If upstream changes a class, the staleness test fails, forcing review of the replacement.

### 9.3 Automatic Coverage

New MAL/LAL YAML rules added to `server-starter/src/main/resources/` are automatically covered by the transpiler and comparison tests -- if the transpiler produces different results from Groovy, the build fails.

---

## 10. Statistics

| Metric | Count |
|--------|-------|
| MAL YAML files processed | 71 |
| MAL metric expressions transpiled | 1,254 |
| MAL filter expressions transpiled | 29 |
| LAL YAML files processed | 8 |
| LAL rules transpiled | 10 (6 unique after SHA-256 dedup) |
| Hierarchy rules replaced | 4 |
| Total generated Java classes | ~1,289 |
| Comparison test assertions | 1,300+ (MAL: 1,281, LAL: 19, hierarchy: 4 rules x multiple service pairs) |
| Lines of transpiler code (MAL) | ~1,230 |
| Lines of transpiler code (LAL) | ~950 |
| Runtime JAR removed | groovy-5.0.3.jar (~7 MB) |

---

## 11. Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Transpiler misses an AST pattern | 1,300 dual-path comparison tests catch any divergence |
| New MAL/LAL expression uses unsupported Groovy syntax | Transpiler throws clear error at build time; new pattern must be added |
| Upstream SampleFamily/Spec changes break replacement | Staleness tests detect SHA-256 changes |
| Performance regression | Eliminated dynamic dispatch should only improve performance; benchmark with `MetricConvert` pipeline |
| Custom user MAL/LAL scripts | Users who extend default rules with custom Groovy scripts must follow the same syntax subset supported by the transpiler |

---

## 12. Migration Timeline

1. **Phase 1**: Add interfaces and functional interface overloads to existing `meter-analyzer` and `log-analyzer` (non-breaking, additive changes)
2. **Phase 2-3**: Implement MAL and LAL transpilers in new `mal-lal-v2/` modules
3. **Phase 4**: Implement v2 runtime loaders (modified `DSL.java`, `Expression.java`, `FilterExpression.java`)
4. **Phase 5**: Hierarchy v1/v2 module split -- refactor `server-core` to remove Groovy, create `hierarchy-v1/` (Groovy, checker-only) and `hierarchy-v2/` (Java lambdas, runtime)
5. **Phase 6**: Build comparison test suites -- `mal-lal-v1-v2-checker/` AND `hierarchy-v1-v2-checker/`
6. **Phase 7**: Switch `server-starter` from v1 to v2 for all three subsystems (MAL, LAL, hierarchy), remove Groovy from runtime classpath
7. **Phase 8**: Replace v2 manifest-based class loading with real ANTLR4 + Javassist compilers following the OAL pattern, enabling runtime compilation when configs change. Rename modules (drop `-v2` suffix), move v1/transpiler/checker to `test/script-compiler/`.
