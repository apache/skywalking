
# OAL V2 Architecture

This package contains the OAL (Observability Analysis Language) engine implementation. V2 is the only implementation after V1 has been completely removed.

## Package Structure

```
org.apache.skywalking.oal.v2/
├── model/                  # Immutable data models
│   ├── SourceLocation.java       # Location in source file
│   ├── SourceReference.java      # from(Service.latency)
│   ├── FunctionCall.java         # longAvg(), percentile2(10)
│   ├── FunctionArgument.java     # Typed function arguments
│   ├── FilterOperator.java       # Enum: ==, !=, >, <, like, in
│   ├── FilterExpression.java     # latency > 100
│   ├── FilterValue.java          # Typed filter values
│   └── MetricDefinition.java     # Complete metric definition
├── registry/               # Service registries
│   ├── MetricsFunctionRegistry.java
│   └── MetricsFunctionDescriptor.java
├── parser/                 # OAL script parsing
│   ├── OALListenerV2.java        # ANTLR parse tree listener
│   └── OALScriptParserV2.java    # Parser facade
├── generator/              # Code generation
│   ├── CodeGenModel.java         # Code generation data model
│   ├── MetricDefinitionEnricher.java  # Metadata enrichment
│   └── OALClassGeneratorV2.java  # Javassist code generator
├── metadata/               # Metadata utilities
│   ├── SourceColumnsFactory.java # Source field lookup
│   ├── SourceColumn.java         # Source field representation
│   ├── FilterMatchers.java       # Filter expression matchers
│   └── MetricsHolder.java        # Metrics function registry
├── util/                   # Code generation utilities
│   ├── ClassMethodUtil.java      # Method name utilities
│   └── TypeCastUtil.java         # Type casting helpers
└── OALEngineV2.java        # Main V2 engine
```

## Design Principles

1. **Immutable Data Models**: All model classes are immutable and thread-safe
2. **Builder Pattern**: Complex objects use fluent builders
3. **Separation of Concerns**: Data ≠ Logic
4. **Dependency Injection**: No static singletons, registries are injectable
5. **Type Safety**: Use enums and types instead of strings where possible

## Model Classes

### SourceLocation

Represents a location in OAL source file for error reporting.

```java
SourceLocation location = SourceLocation.of("core.oal", 25, 10);
System.out.println(location);  // "core.oal:25:10"
```

### SourceReference

Immutable representation of a source reference in OAL.

```java
// from(Service.latency)
SourceReference source = SourceReference.builder()
    .name("Service")
    .addAttribute("latency")
    .build();

// from((long)Service.tag["key"])
SourceReference tagged = SourceReference.builder()
    .name("Service")
    .addAttribute("tag[key]")
    .castType("long")
    .build();

// from(Service.*)
SourceReference wildcard = SourceReference.builder()
    .name("Service")
    .wildcard(true)
    .build();
```

### FilterExpression & FilterValue

Immutable filter expression with typed values.

```java
// latency > 100 (typed as NUMBER)
FilterExpression filter = FilterExpression.builder()
    .fieldName("latency")
    .operator(FilterOperator.GREATER)
    .numberValue(100L)
    .build();

// status == true (typed as BOOLEAN)
FilterExpression filter2 = FilterExpression.builder()
    .fieldName("status")
    .operator(FilterOperator.EQUAL)
    .booleanValue(true)
    .build();

// name like "serv%" (typed as STRING)
FilterExpression filter3 = FilterExpression.builder()
    .fieldName("name")
    .operator(FilterOperator.LIKE)
    .stringValue("serv%")
    .build();

// code in [404, 500, 503] (typed as ARRAY)
FilterExpression filter4 = FilterExpression.builder()
    .fieldName("code")
    .operator(FilterOperator.IN)
    .arrayValue(List.of(404L, 500L, 503L))
    .build();

// Or use shorthand with auto-type detection:
FilterExpression filter5 = FilterExpression.of("latency", ">", 100L);

// Access typed value:
FilterValue value = filter.getValue();
if (value.isNumber()) {
    long num = value.asLong();
}
```

### FunctionCall & FunctionArgument

Represents an aggregation function call with typed arguments.

```java
// longAvg() - no arguments
FunctionCall avgFunc = FunctionCall.of("longAvg");

// percentile2(10) - literal argument
FunctionCall percentile = FunctionCall.builder()
    .name("percentile2")
    .addLiteral(10)
    .build();

// apdex(name, status) - attribute arguments
FunctionCall apdex = FunctionCall.builder()
    .name("apdex")
    .addAttribute("name")
    .addAttribute("status")
    .build();

// rate(status == true, count) - expression and attribute
FunctionCall rate = FunctionCall.builder()
    .name("rate")
    .addExpression(FilterExpression.of("status", "==", true))
    .addAttribute("count")
    .build();

// Or use shorthand for simple literals:
FunctionCall histogram = FunctionCall.ofLiterals("histogram", 100, 20);

// Access typed arguments:
for (FunctionArgument arg : percentile.getArguments()) {
    if (arg.isLiteral()) {
        Object value = arg.asLiteral();
    } else if (arg.isAttribute()) {
        String fieldName = arg.asAttribute();
    } else if (arg.isExpression()) {
        FilterExpression expr = arg.asExpression();
    }
}
```

### MetricDefinition

Complete metric definition combining all components.

```java
// service_resp_time = from(Service.latency).filter(latency > 0).longAvg()
MetricDefinition metric = MetricDefinition.builder()
    .name("service_resp_time")
    .source(SourceReference.of("Service", "latency"))
    .addFilter(FilterExpression.of("latency", ">", 0L))
    .aggregationFunction(FunctionCall.of("longAvg"))
    .location(SourceLocation.of("core.oal", 20, 1))
    .build();

System.out.println(metric.getName());        // "service_resp_time"
System.out.println(metric.getTableName());   // "service_resp_time"
System.out.println(metric.getSource());      // "Service.latency"
System.out.println(metric.getFilters());     // [latency > 0]
```

## Registry Interfaces

### MetricsFunctionRegistry

Service interface for looking up metrics functions.

```java
// Usage (will be injected, not created directly in production)
MetricsFunctionRegistry registry = ...;

Optional<MetricsFunctionDescriptor> longAvg = registry.findFunction("longAvg");
if (longAvg.isPresent()) {
    Class<? extends Metrics> metricsClass = longAvg.get().getMetricsClass();
    Method entranceMethod = longAvg.get().getEntranceMethod();
}

// List all functions
List<String> functionNames = registry.getFunctionNames();
// ["longAvg", "count", "cpm", "percentile2", ...]
```

## Example: Building Complete Metrics

### Example 1: Simple Count with Filter

```java
// OAL: endpoint_error_count = from(Endpoint.*).filter(status == false).count()

MetricDefinition errorCount = MetricDefinition.builder()
    .name("endpoint_error_count")
    .source(SourceReference.builder()
        .name("Endpoint")
        .wildcard(true)
        .build())
    .addFilter(FilterExpression.builder()
        .fieldName("status")
        .operator(FilterOperator.EQUAL)
        .booleanValue(false)
        .build())
    .aggregationFunction(FunctionCall.of("count"))
    .location(SourceLocation.of("core.oal", 42, 1))
    .build();

// Access typed filter value
FilterValue value = errorCount.getFilters().get(0).getValue();
assert value.isBoolean();
assert value.asBoolean() == false;
```

### Example 2: Percentile with Numeric Filter

```java
// OAL: service_slow_percentile = from(Service.latency).filter(latency > 1000).percentile2(10)

MetricDefinition slowPercentile = MetricDefinition.builder()
    .name("service_slow_percentile")
    .source(SourceReference.of("Service", "latency"))
    .addFilter(FilterExpression.builder()
        .fieldName("latency")
        .operator(FilterOperator.GREATER)
        .numberValue(1000L)
        .build())
    .aggregationFunction(FunctionCall.builder()
        .name("percentile2")
        .addLiteral(10)
        .build())
    .build();

// Access typed argument
FunctionArgument arg = slowPercentile.getAggregationFunction().getArguments().get(0);
assert arg.isLiteral();
assert arg.asLiteral().equals(10);
```

### Example 3: Apdex with Attribute Arguments

```java
// OAL: service_apdex = from(Service.latency).apdex(name, status)

MetricDefinition apdexMetric = MetricDefinition.builder()
    .name("service_apdex")
    .source(SourceReference.of("Service", "latency"))
    .aggregationFunction(FunctionCall.builder()
        .name("apdex")
        .addAttribute("name")
        .addAttribute("status")
        .build())
    .build();

// Access typed arguments
List<FunctionArgument> args = apdexMetric.getAggregationFunction().getArguments();
assert args.get(0).isAttribute();
assert args.get(0).asAttribute().equals("name");
assert args.get(1).isAttribute();
assert args.get(1).asAttribute().equals("status");
```

These immutable, type-safe objects can be passed through the pipeline:
**Parse → Semantic Analysis → Validation → Code Generation**

## Key Features

1. **Type Safety**: Strongly typed arguments and values
   - `FunctionArgument` distinguishes LITERAL vs ATTRIBUTE vs EXPRESSION
   - `FilterValue` distinguishes NUMBER vs STRING vs BOOLEAN vs NULL vs ARRAY
   - Compile-time safety instead of runtime casting errors

2. **Testability**: Models can be constructed without parsing
   - Build test data with fluent builders
   - No dependency on ANTLR parser
   - Easy to mock and verify

3. **Immutability**: Thread-safe, no hidden state changes
   - All fields are `final`
   - Collections are unmodifiable
   - No setters, only builders

4. **Clarity**: Explicit builders, no magic
   - Clear method names (`addLiteral()` vs `addAttribute()`)
   - Type-specific accessors (`asLong()`, `asString()`)
   - Self-documenting code

5. **Debuggability**: Clear toString() representations
   - Human-readable output
   - Type information included
   - Easy to inspect in debugger

6. **Validation**: Type checking at construction time
   - Invalid types rejected early
   - Clear error messages
   - Fail fast, not at code generation

## Usage

V2 is now the only OAL implementation. The engine processes OAL scripts through the following pipeline:

1. **Parse**: OALScriptParserV2 parses .oal files into immutable MetricDefinition objects
2. **Enrich**: MetricDefinitionEnricher adds metadata (source columns, persistent fields, etc.)
3. **Generate**: OALClassGeneratorV2 generates Java classes using Javassist and FreeMarker templates

## Implementation Status

✅ **All phases completed**:

1. ✅ **Immutable Model Classes**
   - Type-safe data models with builder patterns
   - Source location tracking for error reporting

2. ✅ **Parser Implementation**
   - Full OAL grammar support via OALListenerV2
   - Converts parse tree to immutable V2 models
   - Validates all production OAL scripts

3. ✅ **Metadata Enrichment**
   - MetricDefinitionEnricher extracts source metadata
   - Reflects on metrics function classes
   - Builds code generation models

4. ✅ **Code Generation**
   - OALClassGeneratorV2 with V2-specific FreeMarker templates
   - Generates metrics, builder, and dispatcher classes
   - Uses Javassist for bytecode generation

5. ✅ **V1 Removal**
   - All V1 implementation code removed
   - V2 is the only OAL engine implementation
   - Utilities reorganized under v2 package structure

## Testing

All V2 models are designed for easy unit testing:

```java
@Test
public void testMetricDefinitionBuilder() {
    MetricDefinition metric = MetricDefinition.builder()
        .name("test_metric")
        .source(SourceReference.of("Service"))
        .aggregationFunction(FunctionCall.of("count"))
        .build();

    assertEquals("test_metric", metric.getName());
    assertEquals("Service", metric.getSource().getName());
    assertEquals("count", metric.getAggregationFunction().getName());
}

@Test
public void testFilterExpression() {
    FilterExpression filter = FilterExpression.of("latency", ">", 100L);

    assertEquals("latency", filter.getFieldName());
    assertEquals(FilterOperator.GREATER, filter.getOperator());
    assertEquals(100L, filter.getValue());
    assertEquals("latency > 100", filter.toString());
}
```

## Architecture

The OAL engine follows a clean pipeline architecture:

1. **OALEngineV2** - Main engine orchestrator (implements OALEngine interface)
2. **OALScriptParserV2** - Parses .oal scripts into immutable MetricDefinition objects
3. **MetricDefinitionEnricher** - Enriches definitions with metadata via reflection
4. **OALClassGeneratorV2** - Generates Java classes using Javassist and FreeMarker
5. **Runtime Integration** - Generated classes integrate with SkyWalking's stream processing

The engine is loaded via reflection in OALEngineLoaderService because server-core compiles before oal-rt in the Maven reactor.
