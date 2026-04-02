# DSL Compiler Design: ANTLR4 + Javassist

## Overview

SkyWalking OAP server uses four domain-specific languages (DSLs) for telemetry analysis.
All four share the same compilation tech stack: **ANTLR4** for grammar parsing and **Javassist** for
runtime bytecode generation.

| DSL | Purpose | Input | Generated Output |
|-----|---------|-------|-----------------|
| **OAL** (Observability Analysis Language) | Trace/mesh metrics aggregation | `.oal` script files | Metrics classes, builders, dispatchers |
| **MAL** (Meter Analysis Language) | Meter/metrics expression evaluation | YAML config `exp` fields | `MalExpression` implementations |
| **LAL** (Log Analysis Language) | Log processing pipelines | YAML config `filter` blocks | `LalExpression` implementations |
| **Hierarchy Matching Rules** | Service hierarchy relationship matching | YAML config expressions | `BiFunction<Service, Service, Boolean>` implementations |

## Compilation Pipeline

All four DSLs follow the same three-phase compilation pipeline at OAP startup:

```
DSL string (from .oal script or YAML config)
    |
    v
Phase 1: ANTLR4 Parsing
    Lexer + Parser (generated from .g4 grammars at build time)
    → Immutable AST model
    |
    v
Phase 2: Java Source Generation
    Walk AST model, emit Java source code as strings
    |
    v
Phase 3: Javassist Bytecode Generation
    ClassPool.makeClass() → CtClass → addMethod(source) → toClass()
    → Ready-to-use class instance loaded into JVM
```

### What Each DSL Generates

| DSL | Interface / Base Class | Key Method |
|-----|----------------------|------------|
| OAL | Extends metrics function class (e.g., `LongAvgMetrics`) | `id()`, `serialize()`, `deserialize()`, plus dispatcher `dispatch(source)` |
| MAL metric | `MalExpression` | `SampleFamily run(Map<String, SampleFamily> samples)` |
| MAL filter | `Predicate<Map<String, String>>` | `boolean test(Map<String, String> tags)` |
| LAL | `LalExpression` | `void execute(FilterSpec filterSpec, ExecutionContext ctx)` |
| Hierarchy | `BiFunction<Service, Service, Boolean>` | `Boolean apply(Service upper, Service lower)` |

OAL is the most complex -- it generates **three classes per metric** (metrics class with storage annotations,
metrics builder for serialization, and source dispatcher for routing), whereas MAL/LAL/Hierarchy each generate
a single functional class per expression.

## ANTLR4 Grammars

Each DSL has its own ANTLR4 lexer and parser grammar. The Maven ANTLR4 plugin generates Java lexer/parser
classes at build time; these are then used at runtime to parse DSL strings.

| DSL | Grammar Location |
|-----|-----------------|
| OAL | `oap-server/oal-grammar/src/main/antlr4/.../OALLexer.g4`, `OALParser.g4` |
| MAL | `oap-server/analyzer/meter-analyzer/src/main/antlr4/.../MALLexer.g4`, `MALParser.g4` |
| LAL | `oap-server/analyzer/log-analyzer/src/main/antlr4/.../LALLexer.g4`, `LALParser.g4` |
| Hierarchy | `oap-server/analyzer/hierarchy/src/main/antlr4/.../HierarchyRuleLexer.g4`, `HierarchyRuleParser.g4` |

## Javassist Constraints

Javassist compiles Java source strings into bytecode but has limitations that shape the code generation:

- **No anonymous inner classes or lambdas** -- Callback-based APIs require workarounds.
  LAL uses private methods called directly from `execute()` instead of Consumer callbacks.
  OAL pre-compiles callbacks as separate `CtClass` instances where needed.
- **No generics in method bodies** -- Generated source uses raw types with explicit casts.
- **Class loading anchor** -- Each DSL uses a `PackageHolder` marker class so that
  `ctClass.toClass(PackageHolder.class)` loads the generated class into the correct module/package
  (required for JDK 9+ module system).

OAL additionally uses **FreeMarker templates** to generate method bodies for metrics classes, builders, and
dispatchers, since these classes are more complex and benefit from template-driven generation.

## Module Structure

```
oap-server/
  oal-grammar/            # OAL: ANTLR4 grammar
  oal-rt/                 # OAL: compiler + runtime (Javassist + FreeMarker)
  analyzer/
    meter-analyzer/       # MAL: grammar + compiler + runtime
    log-analyzer/         # LAL: grammar + compiler + runtime
    hierarchy/            # Hierarchy: grammar + compiler + runtime
    agent-analyzer/       # Calls MAL compiler for meter data
```

OAL keeps grammar and runtime in separate modules (`oal-grammar` and `oal-rt`) because `server-core`
depends on the grammar while the runtime implementation depends on `server-core` (avoiding circular
dependency). MAL, LAL, and Hierarchy are each self-contained in a single module.

## Groovy Replacement (MAL, LAL, Hierarchy)

Reference: [Discussion #13716](https://github.com/apache/skywalking/discussions/13716)

MAL, LAL, and Hierarchy previously used **Groovy** as the runtime scripting engine. OAL has always used
ANTLR4 + Javassist. The Groovy-based DSLs were replaced for the following reasons:

1. **Startup cost** -- 1,250+ `GroovyShell.parse()` calls at OAP boot, each spinning up the full Groovy
   compiler pipeline.

2. **Runtime execution overhead** -- MAL expressions execute on every metrics ingestion cycle. Per-expression
   overhead from dynamic Groovy compounds at scale: property resolution through 4+ layers of indirection,
   `ExpandoMetaClass` closure allocation for simple arithmetic, and megamorphic call sites that defeat JIT
   optimization.

3. **Late error detection** -- MAL uses dynamic Groovy; typos in metric names or invalid method chains are
   only discovered when that specific expression runs with real data.

4. **Debugging complexity** -- Stack traces include Groovy MOP internals (`CallSite`, `MetaClassImpl`,
   `ExpandoMetaClass`), obscuring the actual expression logic.

5. **GraalVM incompatibility** -- `invokedynamic` bootstrapping and `ExpandoMetaClass` are fundamentally
   incompatible with ahead-of-time (AOT) compilation, blocking the
   [GraalVM native-image distribution](https://github.com/apache/skywalking-graalvm-distro).

The DSL grammar for users remains **100% unchanged** -- the same expressions written in YAML config files
work exactly as before. Only the internal compilation engine was replaced.

### Verification: Groovy v1 Checker

DSL script execution tests are maintained under `oap-server/analyzer/dsl-scripts-test/`:

```
oap-server/analyzer/dsl-scripts-test/
  src/test/resources/scripts/
    mal/                      # MAL configs with companion .data.yaml (test-otel-rules, test-meter-analyzer-config, etc.)
    lal/                      # LAL scripts with companion .data.yaml/.input.data (test-lal/)
    hierarchy-rule/           # Hierarchy definition with companion .data.yaml
  src/test/java/.../dsl/tester/
    mal/                      # MALExpressionExecutionTest, MALFilterExecutionTest
    lal/                      # LALExpressionExecutionTest
    hierarchy/                # HierarchyRuleExecutionTest
```

For each DSL expression, the test:

1. Loads test copies of production YAML config files from `scripts/`
2. Compiles with the ANTLR4 + Javassist compiler
3. Executes with mock input data from companion `.data.yaml` files
4. Validates output against expected sections in `.data.yaml` (entities, samples, labels, values)