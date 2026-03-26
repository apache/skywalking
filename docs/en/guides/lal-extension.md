# LAL Extension: Custom Input/Output Types for Developers

LAL (Log Analysis Language) supports custom input and output types via the `LALSourceTypeProvider` SPI.
This allows extensions to define new log processing targets beyond the built-in `Log` type.

## Custom Output Types

### 1. Define a Source Class

Create a source class extending `Source` in `server-core`:

```java
@ScopeDeclaration(id = MY_CUSTOM_SCOPE, name = "MyCustomLog")
public class MyCustomLog extends Source {
    @Getter @Setter private String service;
    @Getter @Setter private String instance;
    // additional fields populated by LAL rules
}
```

### 2. Implement `LALSourceTypeProvider`

```java
package com.example;

import org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider;
import java.util.Map;

public class MySourceTypeProvider implements LALSourceTypeProvider {
    @Override
    public Map<String, Class<?>> sourceTypes() {
        return Map.of("MyCustomLog", MyCustomLog.class);
    }
}
```

### 3. Register via SPI

Create the file `META-INF/services/org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider`:
```
com.example.MySourceTypeProvider
```

### 4. Use in LAL Rules

Reference the custom output type in the rule YAML:

```yaml
rules:
  - name: my_custom_rule
    outputType: MyCustomLog
    dsl: |
      filter {
        // process log data
      }
      extractor {
        service log.service
        instance log.serviceInstance
        // assign to custom fields via output field assignments
      }
```

## Custom Input Types

The `inputType` field in LAL rule YAML specifies the type of the input data. By default, LAL processes
`LogData` (protobuf). Custom input types allow LAL rules to process different data formats
(e.g., Envoy access logs with extra fields):

```yaml
rules:
  - name: envoy_als_rule
    inputType: EnvoyAccessLog
    dsl: |
      filter {
        // access envoy-specific fields
      }
```

The `LALSourceTypeProvider` SPI can register both input and output types. The LAL compiler uses the
type information at compile time to validate field access in the DSL and generate the correct getter
calls in the bytecode.
