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
    K8S_SERVICE: short-name

  POSTGRESQL:
    K8S_SERVICE: short-name

  SO11Y_OAP:
    K8S_SERVICE: short-name

  VIRTUAL_DATABASE:
    MYSQL: lower-short-name-with-fqdn
    POSTGRESQL: lower-short-name-with-fqdn

auto-matching-rules:
  # the name of the upper service is equal to the name of the lower service
  name: "{ (u, l) -> u.name == l.name }"
  # the short name of the upper service is equal to the short name of the lower service
  short-name: "{ (u, l) -> u.shortName == l.shortName }"
  # remove the k8s namespace from the lower service short name
  # this rule is only works on k8s env.
  lower-short-name-remove-ns: "{ (u, l) -> { if(l.shortName.lastIndexOf('.') > 0) return u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.')); return false; } }"
  # the short name of the upper remove port is equal to the short name of the lower service with fqdn suffix
  # this rule is only works on k8s env.
  lower-short-name-with-fqdn: "{ (u, l) -> { if(u.shortName.lastIndexOf(':') > 0) return u.shortName.substring(0, u.shortName.lastIndexOf(':')) == l.shortName.concat('.svc.cluster.local'); return false; } }"

layer-levels:
  # The hierarchy level of the service layer, the level is used to define the order of the service layer for UI presentation.
  # The level of the upper service should greater than the level of the lower service in `hierarchy` section.
  MESH: 3
  GENERAL: 3
  SO11Y_OAP: 3
  VIRTUAL_DATABASE: 3
  MYSQL: 2
  POSTGRESQL: 2
  MESH_DP: 1
  CILIUM_SERVICE: 1
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
- Notice: some hierarchy relations and auto matching rules are only works on k8s env.

### Auto Matching Rules
- The auto matching rules are defined in the `auto-matching-rules` section.
- The matching rules are expressions where the input parameters are the upper service(u) and the lower service(l) and the return value is a boolean,
which are used to match the relation between the upper service(u) and the lower service(l) on the different layers.
- The default matching rules required the service name configured as SkyWalking default and follow the [Showcase](https://github.com/apache/skywalking-showcase).
If you customized the service name in any layer, you should customize the related matching rules according your service name rules.
- A rule reads the two services' fields (`u.name`, `u.shortName`, `u.group`, ...) and may call only:
  - the `String` methods `length`, `isEmpty`, `trim`, `toLowerCase`, `toUpperCase`, `startsWith`, `endsWith`,
    `contains`, `indexOf`, `lastIndexOf`, `substring`, `replace`, `replaceAll`, `replaceFirst`, `split`, `charAt`,
    `equals`, `equalsIgnoreCase`, `matches`, `concat`, `toString`;
  - `Integer.parseInt`, `Integer.valueOf`, `Long.parseLong`, `Long.valueOf`, and `toString()` / `equals()` on any value.

  Any other call fails the rule's compilation with a message. This is the set this release supports; it grows as
  rules need more (propose an addition in an issue or a pull request). A rule is a bare `==` or `!=` comparison
  (`{ (u, l) -> u.name == l.name }`), or a block of `if` / `else if` / `else` and `return`, whose conditions use
  `==`, `!=`, `<`, `<=`, `>`, `>=`, `&&`, `||` and `!`; `+` adds numbers or concatenates a string onto a string, a number or a
  character on its left, and any value but an array or a collection on its right (`"x" + true`; `u + "x"`,
  `u.layers + "x"` and `"x" + u.layers` are refused), `-` subtracts
  numbers (Groovy's `String - String`, which removes the text, is refused; use `replace(...)`). A
  string or character compared with a number reads as the number it is (`"10" > "2"`, `"3" == 3`), and a null orders
  lowest. `==` compares lists and arrays element by element in order (a list against an array too), sets by their
  elements in any order, and maps by their keys and each key's value (`u.name.split('-') == l.name.split('-')`), as
  every DSL does.

### Layer Levels
- Define the hierarchy level of the service layer in the `layer-levels` section.
- The level is used to define the order of the service layer for UI presentation.
- The level of the upper service should greater than the level of the lower service in `hierarchy` section.
