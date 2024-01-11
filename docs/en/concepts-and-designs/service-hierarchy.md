# Service Hierarchy
SkyWalking v10 introduces a new concept `Service Hierarchy` which defines the relationships of existing logically same services in various layers.
OAP will detect the services from different layers, and try to build the connections.

## Detect Service Hierarchy Connections
There 2 ways to detect the connections:
1. Automatically matching through the existing service info.
2. Build the connections through the internal API.

**Note:** All the relationships should be defined in the `config/hierarchy-definition.yml` file.

### Automatically Matching 

| Upper layer | Lower layer  | Matching rule                                                         |
|-------------|--------------|-----------------------------------------------------------------------|
| MESH        | MESH_DP      | upper service name equals lower service name                          |
| MESH        | K8S_SERVICE  | upper service short name equals lower service short name              |
| MESH_DP     | K8S_SERVICE  | upper service short name equals lower service short name              |
| GENERAL     | K8S_SERVICE  | upper service short name equals lower service name without namespace  |

### Build Through Internal API
```java
public void toServiceHierarchyRelation(String upperServiceName, Layer upperServiceLayer, String lowerServiceName, Layer lowerServiceLayer);
```
Use agent tech involved(such as eBPF) and deployment tools(such as operator and agent injector) report 
the service hierarchy relations through the internal API.

| Upper layer | Lower layer  | Matching rule | Detect tech |
|-------------|--------------|---------------|-------------|


# Instance Hierarchy
Instance Hierarchy relationship follows the same definition as Service Hierarchy.

### Automatically Matching
If the service hierarchy is built, the instance hierarchy relationship could be detected automatically through 
the following rules:
1. The upper instance name equals the lower instance name.
2. The upper instance attribute `pod/hostname` equals the lower instance attribute `pod/hostname`.
3. The upper instance attribute `pod/hostname` equals the lower instance name.
4. The upper instance name equals the lower instance attribute `pod/hostname`.

### Build Through Internal API
```java
public void toInstanceHierarchyRelation(String upperInstanceName, String upperServiceName, Layer upperServiceLayer, String lowerInstanceName, String lowerServiceName, Layer lowerServiceLayer);
```
