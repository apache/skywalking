# LAL Extension: Custom Input/Output Types for Developers

LAL (Log Analysis Language) supports custom input and output types for extending log processing
beyond the built-in `Log` type. This enables receiver plugins and custom modules to define
domain-specific log entities (e.g., slow SQL records, sampled traces, network profiling logs).

## Overview

LAL provides two extension mechanisms:

1. **`outputType` per rule** — set in YAML config to transform logs into custom entities
2. **`LALSourceTypeProvider` SPI** — register default input/output types for a receiver plugin

Both are documented in detail in the [LAL user guide](../concepts-and-designs/lal.md#output-type),
including:
- Built-in output types (`SlowSQL`, `SampledTrace`)
- Creating custom output types (Source subclass or `LALOutputBuilder` interface)
- Custom input types for non-standard log formats
- SPI registration for receiver-level defaults

## Quick Reference

### Per-rule output type (YAML config)

```yaml
rules:
  - name: slow_sql
    outputType: SlowSQL
    dsl: |
      filter { ... }
      extractor {
        statement log.body
      }
      sink { }
```

### LALSourceTypeProvider SPI (receiver plugin default)

```java
public class MyLayerSourceTypeProvider implements LALSourceTypeProvider {
    @Override
    public String layer() { return "MY_LAYER"; }

    @Override
    public Class<?> inputType() { return MyProtoMessage.class; }

    @Override
    public Class<? extends Source> outputType() { return MyCustomBuilder.class; }
}
```

Register in `META-INF/services/org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider`.

For complete examples and implementation details, see the
[Output Type section in the LAL documentation](../concepts-and-designs/lal.md#output-type).
