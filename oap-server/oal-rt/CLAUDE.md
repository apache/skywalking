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
│   ├── MetricDefinitionEnricher  # Metadata enrichment
│   └── OALClassGeneratorV2       # Javassist bytecode generator
├── metadata/               # Source/metrics metadata utilities
│   ├── SourceColumnsFactory
│   ├── SourceColumn
│   ├── FilterMatchers
│   └── MetricsHolder
├── util/                   # Code generation utilities
│   ├── ClassMethodUtil
│   └── TypeCastUtil
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

When `SW_DYNAMIC_CLASS_ENGINE_DEBUG=true` environment variable is set, generated `.class` files are written to disk for inspection. Each `.class` is paired with a `<ClassName>.java` sidecar — the verbatim Java source the codegen fed Javassist:

```
{skywalking}/oal-rt/
├── metrics/
│   ├── ServiceRespTimeMetrics.class
│   └── ServiceRespTimeMetrics.java   ← Javassist compile input (synthetic)
└── dispatcher/
    ├── ServiceRelationDispatcher.class
    └── ServiceRelationDispatcher.java
```

The `.java` sidecar exists so IDE source-attach renders the actual codegen input directly without relying on FernFlower / a decompiler. Javassist-emitted bytecode often confuses decompilers (no `goto` consolidation, slot reuse with mixed types, debug-injected `if (gate.isGateOn()) { ... }` chains), and FernFlower frequently bails to "compiled code" stubs. The source-attach path always works and shows the EXACT code Javassist compiled — gate fields, probe call sites, the lot.

When `SW_DSL_DEBUGGING_INJECTION_ENABLED=true` is also set, the codegen flips to the with-debug template (`doMetricsWithDebug.ftl`), which adds the per-metric `GateHolder debug_<metric>` fields and `OALDebug.captureXxx(...)` probe sites to both the `.class` and the `.java` sidecar. The two env vars are independent: `SW_DYNAMIC_CLASS_ENGINE_DEBUG` controls disk dump; `SW_DSL_DEBUGGING_INJECTION_ENABLED` controls codegen branch.

This is useful for debugging code generation issues or comparing V1 vs V2 output.

## Runtime Integration

The engine is loaded via reflection in `OALEngineLoaderService` because `server-core` compiles before `oal-rt` in the Maven reactor. Generated classes integrate with SkyWalking's stream processing pipeline.
