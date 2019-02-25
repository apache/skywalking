# Source and Scope extension for new metric
From [OAL scope introduction](../concepts-and-designs/oal.md#scope), you should already have understood what the scope is.
At here, as you want to do more extension, you need understand deeper, which is the **Source**. 

**Source** and **Scope** are binding concepts. **Scope** declare the id(int) and name, **Source** declare the attributes.
Please follow these steps to create a new Source and Scope.

1. In the OAP core module, it provide **SourceReceiver** internal service.
```java
public interface SourceReceiver extends Service {
    void receive(Source source);
}
```

2. All analysis data must be a **org.apache.skywalking.oap.server.core.source.Source** sub class,
tagged by `@SourceType` annotation, and in `org.apache.skywalking` package.
Then it could be supported by OAL script and OAP core.

Such as existed source, **Service**.
```java
@ScopeDeclaration(id = SERVICE, name = "Service")
public class Service extends Source {
    @Override public int scope() {
        return DefaultScopeDefine.SERVICE;
    }

    @Override public String getEntityId() {
        return String.valueOf(id);
    }

    @Getter @Setter private int id;
    @Getter @Setter private String name;
    @Getter @Setter private String serviceInstanceName;
    @Getter @Setter private String endpointName;
    @Getter @Setter private int latency;
    @Getter @Setter private boolean status;
    @Getter @Setter private int responseCode;
    @Getter @Setter private RequestType type;
}
```

3. The `scope()` method in Source, returns an ID, which is not a random number. This ID need to be declared through 
`@ScopeDeclaration` annotation too. The ID in `@ScopeDeclaration` and ID in `scope()` method should be same for this Source.

4. The `String getEntityId()` method in Source, requests the return value representing unique entity which the scope related. 
Such as,
in this Service scope, the id is service id, representing a particular service, like `Order` service.
This value is used in [OAL group mechanism](../concepts-and-designs/oal.md#group).

5. Add scope name as keyword to oal grammar definition file, `OALLexer.g4`, which is at `antlr4` folder of `generate-tool-grammar` module.

6. Add scope name keyword as source in parser definition file, `OALParser.g4`, which is at same fold of `OALLexer.g4`.

7. Set the default columns for new scope, at `generator-scope-meta.yml` file in `generated-analysis/src/main/resources`.
If you want to understand why need these columns, you have to understand all existing query(s). But there is an easy way, 
follow other existing scopes. Such as, if you are adding metric, connection number for service instance, follow existing `ServiceInstance`. 

___
After you done all of these, you could build a receiver, which do
1. Get the original data of the metric,
1. Build the source, send into `SourceReceiver`.
1. Write your whole OAL scripts.
1. Repackage the project.