# Define Service Hierarchy
SkyWalking v10 introduces a new concept `Service Hierarchy` which defines the relationships of existing logically same services in various layers.
The concept and design could be found [here](service-hierarchy.md).

## Service Hierarchy Configuration
All the relationships defined in the `config/hierarchy-definition.yml` file. You can customize it according to your own needs.
Here is an example:

```yaml
hierarchy:
  MESH:
    MESH_DP: name
    K8S_SERVICE: short-name

  MESH_DP:
    K8S_SERVICE: short-name

  GENERAL:
    K8S_SERVICE: lower-short-name-remove-ns

  MYSQL:
    K8S_SERVICE: ~

  VIRTUAL_DATABASE:
    MYSQL: ~

auto-matching-rules:
  # the name of the upper service is equal to the name of the lower service
  name: "{ (u, l) -> u.name == l.name }"
  # the short name of the upper service is equal to the short name of the lower service
  short-name: "{ (u, l) -> u.shortName == l.shortName }"
  # remove the namespace from the lower service short name
  lower-short-name-remove-ns: "{ (u, l) -> u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.')) }"

layer-levels:
  # The hierarchy level of the service layer, the level is used to define the order of the service layer for UI presentation.
  # The level of the upper service should greater than the level of the lower service in `hierarchy` section.
  MESH: 3
  GENERAL: 3
  VIRTUAL_DATABASE: 3
  MYSQL: 2
  MESH_DP: 1
  K8S_SERVICE: 0
```

### Hierarchy
- The hierarchy of service layers are defined in the `hierarchy` section.
- The layers under the specific layer are related lower of the layer.
- The relation could have a matching rule for auto matching, which are defined in the `auto-matching-rules` section.
- The relation without a matching rule should be built through the internal API.
- All the layers are defined in the file `org.apache.skywalking.oap.server.core.analysis.Layers.java`.
- If the hierarchy is not defined, the service hierarchy relationship will not be built.
- If you want to add a new relationship, you should certainly know they can be matched automatically by [Auto Matching Rules](#auto-matching-rules).

### Auto Matching Rules
- The auto matching rules are defined in the `auto-matching-rules` section.
- Use Groovy script to define the matching rules, the input parameters are the upper service(u) and the lower service(l) and the return value is a boolean, 
which are used to match the relation between the upper service(u) and the lower service(l) on the different layers.

### Layer Levels
- Define the hierarchy level of the service layer in the `layer-levels` section.
- The level is used to define the order of the service layer for UI presentation.
- The level of the upper service should greater than the level of the lower service in `hierarchy` section.
