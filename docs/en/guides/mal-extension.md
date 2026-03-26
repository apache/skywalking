# MAL Extension Functions for Developers

MAL (Meter Analysis Language) supports custom extension functions callable from scripts using the
`namespace::method()` syntax. Extensions are discovered at startup via Java `ServiceLoader`, requiring
no changes to the MAL compiler or grammar.

## Creating an Extension

### 1. Implement `MalFunctionExtension`

Create a class that implements the SPI interface and add static methods annotated with `@MALContextFunction`:

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

### 2. Register via SPI

Create the file `META-INF/services/org.apache.skywalking.oap.meter.analyzer.v2.spi.MalFunctionExtension`:
```
com.example.MyExtension
```

### 3. Use in MAL scripts

```yaml
metricsRules:
  - name: scaled_metric
    exp: metric.sum(['svc']).myext::scale(2.0)
  - name: filtered_metric
    exp: metric.myext::filterByTag("env", "prod").sum(['svc'])
```

## Method Requirements

- Methods **must** be `static`
- First parameter **must** be `SampleFamily` (auto-bound to the current chain value in the expression)
- Return type **must** be `SampleFamily`
- Non-static or invalid methods throw `IllegalArgumentException` at startup
- Duplicate namespace names throw `IllegalArgumentException` at startup
- Duplicate method names within the same namespace throw `IllegalArgumentException` at startup

## Supported Parameter Types

| Java Type | MAL Argument | Example |
|-----------|-------------|---------|
| `String` | String literal | `"value"` |
| `double` | Number literal | `2.0` |
| `float` | Number literal | `3.0` |
| `long` | Number literal | `100` |
| `int` | Number literal | `10` |
| `List<String>` | String list | `["tag1", "tag2"]` |

Only `List<String>` is supported for list parameters. Other generic list types (e.g., `List<Integer>`)
are rejected at startup.

## How It Works

The MAL compiler generates **direct static method calls** at compile time — no reflection at runtime.
Each metric gets a named variable (e.g., `_metric`), and extension calls are emitted as static calls
on the variable:

For `metric.sum(['svc']).myext::scale(2.0)`:
```java
SampleFamily _metric = ((SampleFamily) samples.getOrDefault("metric", SampleFamily.EMPTY));
_metric = _metric.sum(java.util.Arrays.asList(new String[]{"svc"}));
_metric = com.example.MyExtension.scale(_metric, 2.0);
return _metric;
```

## Compile-Time Validation

The compiler validates at expression compilation time:
- Namespace exists in the SPI registry
- Method exists in that namespace
- Argument count matches (excluding the implicit `SampleFamily` first parameter)
- Argument types are compatible with the method signature

Any validation failure results in a compilation error with a clear message.

## Shared Utilities

### GenAI Model Matcher

The `GenAIModelMatcher` in `server-library/library-util` provides Trie-based model name matching
with alias support, available to MAL extensions:

```java
import org.apache.skywalking.oap.server.library.util.genai.GenAIModelMatcher;

GenAIModelMatcher matcher = GenAIModelMatcher.getInstance();
GenAIModelMatcher.MatchResult result = matcher.match("gpt-4o-2024-08-06");
// result.getProvider() = "openai"
// result.getModelConfig().getInputEstimatedCostPerM() = 2.5
```
