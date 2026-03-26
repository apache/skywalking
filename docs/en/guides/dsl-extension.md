# DSL Extension Guide for Developers

SkyWalking's analysis DSLs (MAL, LAL, OAL) are extensible via Java SPI. This guide covers how developers
can add custom functions and capabilities to each DSL without modifying the core compiler.

## MAL Extension Functions (`namespace::method()`)

MAL supports custom extension functions callable from scripts using the `namespace::method()` syntax.
Extensions are discovered at startup via Java `ServiceLoader`.

### Creating an Extension

1. Implement `MalFunctionExtension` with static `@MALContextFunction` methods:

```java
package com.example;

import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.spi.MALContextFunction;
import org.apache.skywalking.oap.meter.analyzer.v2.spi.MalFunctionExtension;

public class MyExtension implements MalFunctionExtension {
    @Override
    public String name() {
        return "myext";  // namespace used in MAL scripts
    }

    @MALContextFunction
    public static SampleFamily scale(SampleFamily sf, double factor) {
        return sf.multiply(Double.valueOf(factor));
    }

    @MALContextFunction
    public static SampleFamily filterByTag(SampleFamily sf, String key, String value) {
        return sf.tagEqual(key, value);
    }
}
```

2. Register via SPI file `META-INF/services/org.apache.skywalking.oap.meter.analyzer.v2.spi.MalFunctionExtension`:
```
com.example.MyExtension
```

3. Use in MAL scripts:
```yaml
metricsRules:
  - name: scaled_metric
    exp: metric.sum(['svc']).myext::scale(2.0)
  - name: filtered_metric
    exp: metric.myext::filterByTag("env", "prod").sum(['svc'])
```

### Method Requirements

- Methods **must** be `static`
- First parameter **must** be `SampleFamily` (auto-bound to the current chain value)
- Return type **must** be `SampleFamily`
- Non-static or invalid methods throw `IllegalArgumentException` at startup
- Duplicate namespace names throw `IllegalArgumentException` at startup

### Supported Parameter Types

| Java Type | MAL Argument | Example |
|-----------|-------------|---------|
| `String` | String literal | `"value"` |
| `double` | Number literal | `2.0` |
| `float` | Number literal | `3.0` |
| `long` | Number literal | `100` |
| `int` | Number literal | `10` |
| `List<String>` | String list | `["tag1", "tag2"]` |

### Generated Code

The MAL compiler generates **direct static method calls** — no reflection at runtime.
Each metric gets a named variable (e.g., `_metric`):

For `metric.sum(['svc']).myext::scale(2.0)`:
```java
SampleFamily _metric = ((SampleFamily) samples.getOrDefault("metric", SampleFamily.EMPTY));
_metric = _metric.sum(java.util.Arrays.asList(new String[]{"svc"}));
_metric = com.example.MyExtension.scale(_metric, 2.0);
return _metric;
```

### Compile-Time Validation

The compiler validates at expression compilation time:
- Namespace exists in the SPI registry
- Method exists in that namespace
- Argument count matches (excluding the implicit `SampleFamily` first parameter)
- Argument types are compatible with the method signature

## LAL Custom Output Types

LAL supports custom log output types via the `LALSourceTypeProvider` SPI. This allows extensions to
define new log processing targets beyond the built-in `Log` type.

### Creating a Custom Output Type

1. Define a source class extending `Source` in `server-core`:

```java
@ScopeDeclaration(id = MY_CUSTOM_SCOPE, name = "MyCustomLog")
public class MyCustomLog extends Source {
    // fields populated by LAL rules
}
```

2. Implement `LALSourceTypeProvider`:

```java
public class MySourceTypeProvider implements LALSourceTypeProvider {
    @Override
    public Map<String, Class<?>> sourceTypes() {
        return Map.of("MyCustomLog", MyCustomLog.class);
    }
}
```

3. Register via SPI file `META-INF/services/org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider`.

4. Use in LAL rules with `outputType`:
```yaml
rules:
  - name: my_custom_rule
    outputType: MyCustomLog
    dsl: |
      filter { ... }
```

## OAL Source Extension

To add new metrics sources for OAL analysis, see [Extend An OAL Source](source-extension.md).

## Shared Utilities

### GenAI Model Matcher (`library-util`)

The `GenAIModelMatcher` in `server-library/library-util` provides Trie-based model name matching
with alias support, available to both MAL extensions and agent analyzers:

```java
import org.apache.skywalking.oap.server.library.util.genai.GenAIModelMatcher;

// Singleton — lazy initialized from gen-ai-config.yml
GenAIModelMatcher matcher = GenAIModelMatcher.getInstance();
GenAIModelMatcher.MatchResult result = matcher.match("gpt-4o-2024-08-06");
// result.getProvider() = "openai"
// result.getModelConfig().getInputEstimatedCostPerM() = 2.5
```

This is used by the GenAI cost estimation extension for SWIP-10 AI Gateway monitoring.
