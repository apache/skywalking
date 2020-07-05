# Source and Scope extension for new metrics
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
@ScopeDeclaration(id = SERVICE_INSTANCE, name = "ServiceInstance", catalog = SERVICE_INSTANCE_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class ServiceInstance extends Source {
    @Override public int scope() {
        return DefaultScopeDefine.SERVICE_INSTANCE;
    }

    @Override public String getEntityId() {
        return String.valueOf(id);
    }

    @Getter @Setter private int id;
    @Getter @Setter @ScopeDefaultColumn.DefinedByField(columnName = "service_id") private int serviceId;
    @Getter @Setter private String name;
    @Getter @Setter private String serviceName;
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

5. `@ScopeDefaultColumn.VirtualColumnDefinition` and `@ScopeDefaultColumn.DefinedByField` are required, all declared fields(virtual/byField)
are going to be pushed into persistent entity, mapping to such as ElasticSearch index and Database table column.
Such as, include entity id mostly, and service id for endpoint and service instance level scope. Take a reference to all existing scopes.
All these fields are detected by OAL Runtime, and required in query stage.

6. Add scope name as keyword to oal grammar definition file, `OALLexer.g4`, which is at `antlr4` folder of `generate-tool-grammar` module.

7. Add scope name keyword as source in parser definition file, `OALParser.g4`, which is at same fold of `OALLexer.g4`.


___
After you done all of these, you could build a receiver, which do
1. Get the original data of the metrics,
1. Build the source, send into `SourceReceiver`.
1. Write your whole OAL scripts.
1. Repackage the project.