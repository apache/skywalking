# Abstract
**apm-ui-protocol** declares all services, using GraphQL API style, which provide by Collector UI module.

## Services
### [Common](common.graphqls)

Include common objects, which used in global

### [Overview Layer Service](overview-layer.graphqls)

Query data without specific application, server or service. It includes info for overview the whole cluster.

### [Application Layer Service](application-layer.graphqls)

Query application related data with specific application code.

### [Server Layer Service](server-layer.graphqls)

Query server related data with specific server id.

### [Service Layer Service](service-layer.graphqls)

Query service related data with specific service id

### [Trace Service](trace.graphqls)

Query trace by some conditions.

### [Alarm Service](alarm.graphqls)

Query alarm info.

## Version
v1alpha1

### Versioning
Use URI Versioning, to follow the most straightforward approach, 
though it does violate the principle that a URI should refer to a unique resource.

e.g.
http://collector.host/graphql/v1alpha1
