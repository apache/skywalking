# OAL V2 Engine

The OAL (Observability Analysis Language) engine for Apache SkyWalking. This is the only OAL implementation.

## Package Structure

```
org.apache.skywalking.oal.v2/
├── model/                  # Immutable data models (parser output)
│   ├── SourceLocation      # Location in source file for error reporting
│   ├── SourceReference     # from(Service.latency)
│   ├── FunctionCall        # longAvg(), percentile2(10)
│   ├── FunctionArgument    # Typed function arguments
│   ├── FilterOperator      # Enum: ==, !=, >, <, like, in
│   ├── FilterExpression    # latency > 100
│   ├── FilterValue         # Typed filter values
│   └── MetricDefinition    # Complete parsed metric
├── parser/                 # OAL script parsing
│   ├── OALListenerV2       # ANTLR parse tree listener
│   └── OALScriptParserV2   # Parser facade
├── generator/              # Code generation
│   ├── CodeGenModel        # Code generation data model
│   ├── MetricDefinitionEnricher  # Metadata enrichment and compile checks
│   ├── FilterCompiler      # Filters and condition arguments → matcher calls
│   ├── SourceAttribute     # Attribute path → getter chain and type, each step checked by OALCallables
│   ├── OALCallables        # What an attribute path may read (the shared DslCallables, OAL's list)
│   ├── OALClassGeneratorV2       # Javassist bytecode generator
│   └── StringComparison    # Strings compared as written
├── metadata/               # Source/metrics metadata utilities
│   ├── SourceColumnsFactory
│   ├── SourceColumn
│   ├── FilterMatchers
│   └── MetricsHolder
├── util/                   # Code generation utilities
│   ├── ClassMethodUtil
│   └── TypeCastUtil        # Value conversions: casts, and the literals as the generated code reads them
└── OALEngineV2             # Main engine entry point
```

## Pipeline

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  .oal file  │───▶│   Parser    │───▶│  Enricher   │───▶│  Generator  │
│             │    │             │    │             │    │             │
│ OAL script  │    │ MetricDef   │    │ CodeGenModel│    │ Bytecode/   │
│             │    │ (immutable) │    │ (metadata)  │    │ Source      │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
```

1. **Parser** (`OALScriptParserV2`): Parses `.oal` files into immutable `MetricDefinition` objects
2. **Enricher** (`MetricDefinitionEnricher`): Adds metadata via reflection (source columns, persistent fields)
3. **Generator** (`OALClassGeneratorV2`): Generates Java classes using Javassist and FreeMarker templates

## Fail Loud

The forms below take effect, fail the compile, or, where they cannot change the result, compile with a warning; none is
dropped or rewritten in silence. Other silent paths remain, such as `contain` on a list whose elements are not strings.
A check refuses only a form whose generated code would be silently wrong: a form whose generated code runs as written
compiles and resolves that way, one whose generated code fails loudly (in Javassist, at class load or at the first
event) is left to fail that way, and a form whose generated code would not compile is taken only with a check of what
it reads. Compile-time lookups have no side effect, and a check whose lookup is not certain to see what Javassist will
use is skipped.

- **Grammar**: `root` ends at `EOF`. `/* */` nests, and a `/` or `*` inside a comment cannot start or end one, so an
  unclosed inner comment or a stray `*/` fails to lex. A function takes at most two arguments. The lexer and the
  parser report to `OALErrorListener`, which collects the errors; `OALScriptParserV2` throws them after `root`,
  before the listener walks the tree.
- **Parser** (`OALListenerV2`): literals are read by `TypeCastUtil`, the one place for OAL's value conversions. A number
  in a filter drops `_` and the `L` suffix but stays a decimal number, and with either a leading `0`, which Java would
  read as octal, fails; a function argument number is pasted into the call, without `L` its decimal value and with `L`
  its spelling (`FunctionArgument#isLongSuffixed`), passed to Javassist as written with no check of its value or range,
  and with `_` fails. String literals are decoded once, as Javassist reads no escape but `\n \t \r \f` and takes any
  other character as written; a decorator name stays as written, as the templates paste it into a Java string. A filter
  value keeps its form as written where that differs (`FilterValue#getWrittenForm`): a number with `L` or `_` as its
  text, a string undecoded, a list's items as text. Casts are kept apart from the attribute path
  (`FilterExpression#getCastType`, `FunctionArgument#getCastType`); enum constants inside `in [...]` are `FilterValue`
  enums, so a quoted `"a.b"` stays a string; `from(X.attr.*)` fails.
- **Enricher** (`MetricDefinitionEnricher`): emits the getter chain of the attributes a rule reads (`SourceAttribute`). A
  type is taken only from a public no-arg getter, which is the method Javassist calls too; past a keyed getter, or a
  class whose members reflection cannot read (a missing optional dependency), the type is unknown and its checks are
  skipped. Classes are loaded without being initialized, and nothing is created at compile. `TYPE.NAME` is written as a
  constant of the source-package class `TYPE`, with no lookup of its own. A keyed segment is taken where a path split at
  each dot reads it (`SourceAttribute#checkKeyedPath`): any in from(); in a filter the whole attribute, or a segment
  before the last of a dotted path; in a function argument a segment of a dotted path; never with a dotted key or read
  as a boolean, outside from() and the filter's whole attribute. A number out of the int range is a long literal unless
  the field is a known int or double. A value is rejected only where its comparison is certain never to hold from the
  declared types (an enum against a string, a string in `in` against a field that cannot hold one, as a `Collection` or
  a `Layer`, and a string with a dot, which names a constant as written, against a field of unseen type), or would
  change: a number out of an `int` field's range, a `like` pattern without `%`. Filters and condition arguments compile
  in `FilterCompiler`: every filter-value fitting and check (the exact list, the object compare, the long literal and
  range check, the `contain` re-quoting, the `like` pattern check, the enum-against-string rejection, the list checks)
  applies to the stock matchers only; a matcher registered under an operator's name gets the value and the attribute as
  written (`SourceAttribute#asWritten`). A cast is accepted in from() only: elsewhere a value it cannot parse would
  throw out of the whole dispatch. Strings compare as written (`StringComparison`): a string or null `==` as objects, a
  list holding strings, or constants for a field that may hold a string, through `ExactInMatch`, and `contain` with a
  quoted string wrapped once more; a list of whole numbers takes `InMatch`'s `long[]` overloads. A surplus argument is
  rejected, and a missing one but for a `@DefaultValue` parameter, which would leave Javassist to call a shorter
  overload; a condition passes to an `@Expression` parameter only, `@DefaultValue` takes a literal, a number without
  `L`, an int for Javassist whatever the parameter, is in the int range and at least `@Arg#min()`, one with `L` is
  written as is for the overload Javassist picks, a string literal, which would be pasted as Java code, is rejected, a
  `double` into an integral parameter is rejected, and the last bucket of the stock `HistogramMetrics` entrance fits in
  an int. Values pass to the entrance through `TypeCastUtil#withCast` and `#toParameter`, a boolean as 1 or 0; any other
  value the generated code cannot pass fails in Javassist or at class load. A from() attribute no function reads is
  logged, not rejected. Errors are `IllegalArgumentException`s prefixed with `file:line:column metric_name:`, which
  `OALEngineV2` wraps without naming the metric again.
- **Generator** (`OALClassGeneratorV2`): each generated class is defined and its constructor looked up, which links it,
  so a class the JVM verifier rejects fails the compile with an `OALCompileException` naming the rule, not the boot as
  an `Error`; no instance is made, so no constructor runs before its stream is registered. A `disable(name)` of a stream
  already defined has no effect, is not registered, and is logged (only `oal/disable.oal` loads before the streams).
  A disabled metric is still dispatched: its decorator may feed the rules after it.
- **disable()**: a name no defined stream carries may be a metric of an OAL script this OAP does not load, a MAL
  metric, or a typo; `CoreModuleProvider#notifyAfterCompleted` logs `DisableRegister#scriptDisablesWithoutStream()`.

`OALScriptChecksTest` covers the enricher and generator rules, compiling and dispatching through `OALScriptRunner`
where the rule is behaviour rather than a rejection; `OALScriptParserV2Test` covers the grammar and the literals, and
server-core's `DisableRegisterTest` the disable register.

## Design Principles

1. **Immutable Models**: All parser output classes are immutable and thread-safe
2. **Type Safety**: Use enums and typed values instead of strings
3. **Builder Pattern**: Complex objects use fluent builders
4. **Separation of Concerns**: Parser models ≠ Code generation models
5. **Testability**: Models can be constructed without parsing for unit tests

## Key Classes

### MetricDefinition (Parser Output)

Immutable representation of a parsed OAL metric:

```java
// service_resp_time = from(Service.latency).filter(latency > 0).longAvg()
MetricDefinition metric = MetricDefinition.builder()
    .name("service_resp_time")
    .source(SourceReference.of("Service", "latency"))
    .addFilter(FilterExpression.of("latency", ">", 0L))
    .aggregationFunction(FunctionCall.of("longAvg"))
    .build();
```

### CodeGenModel (Generator Input)

Enriched model with metadata for code generation:

```java
// Created by MetricDefinitionEnricher
CodeGenModel model = enricher.enrich(metricDefinition);

// Contains: source columns, persistent fields, metrics class info, etc.
model.getFieldsFromSource();     // Fields copied from source
model.getPersistentFields();     // Fields for storage
model.getMetricsClassName();     // e.g., "LongAvgMetrics"
```

## Debug Output

When `SW_DYNAMIC_CLASS_ENGINE_DEBUG=true` environment variable is set, generated `.class` files are written to disk for inspection. Each `.class` is paired with a `<ClassName>.java` generated source file — the verbatim Java source the codegen fed Javassist:

```
{skywalking}/oal-rt/
├── metrics/
│   ├── ServiceRespTimeMetrics.class
│   └── ServiceRespTimeMetrics.java   ← Javassist compile input (synthetic)
└── dispatcher/
    ├── ServiceRelationDispatcher.class
    └── ServiceRelationDispatcher.java
```

The `.java` generated source file exists so IDE source-attach renders the actual codegen input directly without relying on FernFlower / a decompiler. Javassist-emitted bytecode often confuses decompilers (no `goto` consolidation, slot reuse with mixed types, debug-injected `if (gate.isGateOn()) { ... }` chains), and FernFlower frequently bails to "compiled code" stubs. The source-attach path always works and shows the EXACT code Javassist compiled — gate fields, probe call sites, the lot.

When `SW_DSL_DEBUGGING_INJECTION_ENABLED=true` is also set, the codegen flips to the with-debug template (`doMetricsWithDebug.ftl`), which adds the per-metric `GateHolder debug_<metric>` fields and `OALDebug.captureXxx(...)` probe sites to both the `.class` and the `.java` generated source file. The two env vars are independent: `SW_DYNAMIC_CLASS_ENGINE_DEBUG` controls disk dump; `SW_DSL_DEBUGGING_INJECTION_ENABLED` controls codegen branch.

This is useful for debugging code generation issues or comparing V1 vs V2 output.

## Runtime Integration

The engine is loaded via reflection in `OALEngineLoaderService` because `server-core` compiles before `oal-rt` in the Maven reactor. Generated classes integrate with SkyWalking's stream processing pipeline.
