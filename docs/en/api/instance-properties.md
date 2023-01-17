# Report service instance status
1. Service Instance Properties
   Service instance contains more information than just a name. In order for the agent to report service instance status, use `ManagementService#reportInstanceProperties` service to provide a string-key/string-value pair list as the parameter. The `language` of target instance must be provided as the minimum requirement.

2. Service Ping
   Service instance should keep alive with the backend. The agent should set a scheduler using `ManagementService#keepAlive` service every minute.


```protobuf
syntax = "proto3";

package skywalking.v3;

option java_multiple_files = true;
option java_package = "org.apache.skywalking.apm.network.management.v3";
option csharp_namespace = "SkyWalking.NetworkProtocol.V3";
option go_package = "skywalking.apache.org/repo/goapi/collect/management/v3";

import "common/Common.proto";
import "common/Command.proto";

// Define the service reporting the extra information of the instance.
service ManagementService {
    // Report custom properties of a service instance.
    rpc reportInstanceProperties (InstanceProperties) returns (Commands) {
    }

    // Keep the instance alive in the backend analysis.
    // Only recommend to do separate keepAlive report when no trace and metrics needs to be reported.
    // Otherwise, it is duplicated.
    rpc keepAlive (InstancePingPkg) returns (Commands) {

    }
}

message InstanceProperties {
    string service = 1;
    string serviceInstance = 2;
    repeated KeyStringValuePair properties = 3;
    // Instance belong layer name which define in the backend, general is default.
    string layer = 4;
}

message InstancePingPkg {
    string service = 1;
    string serviceInstance = 2;
    // Instance belong layer name which define in the backend, general is default.
    string layer = 3;
}
```

## Via HTTP Endpoint

- Report service instance properties

> POST http://localhost:12800/v3/management/reportProperties

Input:

```json
{
	"service": "User Service Name",
	"serviceInstance": "User Service Instance Name",
	"properties": [
		{ "key": "language", "value": "Lua" }
	]
}
```

Output JSON Array:

```json
{}
```

- Service instance ping

> POST http://localhost:12800/v3/management/keepAlive

Input:

```json
{
	"service": "User Service Name",
	"serviceInstance": "User Service Instance Name"
}
```

OutPut:

```json
{}
```