# Source and scope extension for new metrics
From the [OAL scope introduction](../concepts-and-designs/oal.md#scope), you should already have understood what a scope is.
If you would like to create more extensions, you need to have a deeper understanding of what a **source** is. 

**Source** and **scope** are interrelated concepts. **Scope** declares the ID (int) and name, while **source** declares the attributes.
Follow these steps to create a new source and sccope.

1. In the OAP core module, it provides **SourceReceiver** internal services.
```java
public interface SourceReceiver extends Service {
    void receive(Source source);
}
```

2. All data of the analysis must be a **org.apache.skywalking.oap.server.core.source.Source** sub class that is
tagged by `@SourceType` annotation, and included in the `org.apache.skywalking` package. Then, it can be supported by the OAL script and OAP core.

Take the existing source **service** as an example.
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

3. The `scope()` method in source returns an ID, which is not a random value. This ID must be declared through the `@ScopeDeclaration` annotation too. The ID in `@ScopeDeclaration` and ID in `scope()` method must be the same for this source.

4. The `String getEntityId()` method in source requests the return value representing the unique entity to which the scope relates. For example, in this service scope, the ID is the service ID, which represents a particular service, like the `Order` service.
This value is used in the [OAL group mechanism](../concepts-and-designs/oal.md#group).

5. `@ScopeDefaultColumn.VirtualColumnDefinition` and `@ScopeDefaultColumn.DefinedByField` are required. All declared fields (virtual/byField) will be pushed into a persistent entity, and maps to lists such as the ElasticSearch index and Database table column.
For example, the entity ID and service ID for endpoint and service instance level scope are usually included. Take a reference from all existing scopes.
All these fields are detected by OAL Runtime, and are required during query.

6. Add scope name as keyword to OAL grammar definition file, `OALLexer.g4`, which is at the `antlr4` folder of the `generate-tool-grammar` module.

7. Add scope name as keyword to the parser definition file, `OALParser.g4`, which is located in the same folder as `OALLexer.g4`.


___
After finishing these steps, you could build a receiver, which do
1. Obtain the original data of the metrics.
1. Build the source, and send to `SourceReceiver`.
1. Complete your OAL scripts.
1. Repackage the project.
