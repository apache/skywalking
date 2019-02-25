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

2. All analysis data must be a **org.apache.skywalking.oap.server.core.source.Source**,
tagged by `@SourceType` annotation,
so it could be supported by OAL script and OAP core.

Such as existed source, **Service**.
```java
@SourceType
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
`@ScopeDeclaration` annotation. This annotation could be used in any class in `org.apache.skywalking` package. 
But just for code style, if this scope is provided in our ASF official repo, we recommend and ask you to add it at
`org.apache.skywalking.oap.server.core.source.DefaultScopeDefine`, like the existing ones.
```java
@ScopeDeclaration(id = ALL, name = "All")
@ScopeDeclaration(id = SERVICE, name = "Service")
@ScopeDeclaration(id = SERVICE_INSTANCE, name = "ServiceInstance")
@ScopeDeclaration(id = ENDPOINT, name = "Endpoint")
@ScopeDeclaration(id = SERVICE_RELATION, name = "ServiceRelation")
@ScopeDeclaration(id = SERVICE_INSTANCE_RELATION, name = "ServiceInstanceRelation")
@ScopeDeclaration(id = ENDPOINT_RELATION, name = "EndpointRelation")
@ScopeDeclaration(id = NETWORK_ADDRESS, name = "NetworkAddress")
@ScopeDeclaration(id = SERVICE_INSTANCE_JVM_CPU, name = "ServiceInstanceJVMCPU")
@ScopeDeclaration(id = SERVICE_INSTANCE_JVM_MEMORY, name = "ServiceInstanceJVMMemory")
@ScopeDeclaration(id = SERVICE_INSTANCE_JVM_MEMORY_POOL, name = "ServiceInstanceJVMMemoryPool")
@ScopeDeclaration(id = SERVICE_INSTANCE_JVM_GC, name = "ServiceInstanceJVMGC")
@ScopeDeclaration(id = SEGMENT, name = "Segment")
@ScopeDeclaration(id = ALARM, name = "Alarm")
@ScopeDeclaration(id = SERVICE_INVENTORY, name = "ServiceInventory")
@ScopeDeclaration(id = SERVICE_INSTANCE_INVENTORY, name = "ServiceInstanceInventory")
@ScopeDeclaration(id = ENDPOINT_INVENTORY, name = "EndpointInventory")
@ScopeDeclaration(id = DATABASE_ACCESS, name = "DatabaseAccess")
@ScopeDeclaration(id = DATABASE_SLOW_STATEMENT, name = "DatabaseSlowStatement")
public class DefaultScopeDefine {
    ...
}
```

4. The `String getEntityId()` method in Source, request the return value representing unique entity which the scope related. 
Such as,
in this Service scope, the id is service id, which is used in [OAL group mechanism](../concepts-and-designs/oal.md#group).

5. Add scope name as keyword to oal grammar definition file, `OALLexer.g4`, which is at `antlr4` folder of `generate-tool-grammar` module.

6. Add scope name keyword as source in parser definition file, `OALParser.g4`, which is at same fold of `OALLexer.g4`.

7. Set the default columns for new scope, at `generator-scope-meta.yml` file in `generated-analysis/src/main/resources`.
If you want to understand why need this columns, you have to understand all existing query(s). But there is an easy way, 
follow other existing scopes. Such as, if you are adding metric, connection number for service instance, follow existing `ServiceInstance`. 

___
After you done all of these, you could build a receiver, which do
1. Get the original data of the metric,
1. Build the source, send into `SourceReceiver`.
1. Write your whole OAL scripts.
1. Repackage the project.