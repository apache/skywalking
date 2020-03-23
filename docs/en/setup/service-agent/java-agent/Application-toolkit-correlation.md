## Cross Process Correlation

## Introduce
This plugin is help user to transport custom data in the tracing context. [Follow this to get protocol description.](../../../protocols/Skywalking-Cross-Process-Correlation-Headers-Protocol-v1.md)

## How to use it
* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-trace</artifactId>
      <version>${skywalking.version}</version>
   </dependency>
```

* Use `CorrelationContext.set()` API to setting custom data.
```java
CorrelationSettingResult settingResult = CorrelationContext.set("customKey", "customValue");
```
_Sample codes only_

1. Add `CorrelationContext.set` to setting your custom data.
1. Key and value only support `String` type.
1. Please follow [the agent configuration](README.md#table-of-agent-configuration-properties) to get more setting limit.

* Use `CorrelationContext.get()` API to get custom data.
```java
CorrelationContext.get("customKey");
```
_Sample codes only_

1. `CorrelationContext.get` to get custom data.
1. Return empty string if cannot found the custom key.