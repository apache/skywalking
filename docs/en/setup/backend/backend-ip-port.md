# IP and port setting
The backend uses IP and port binding in order to allow the OS to have multiple IPs.
The binding/listening IP and port are specified by the core module
```yaml
core:
  default:
    restHost: 0.0.0.0
    restPort: 12800
    restContextPath: /
    gRPCHost: 0.0.0.0
    gRPCPort: 11800
```
There are two IP/port pairs for gRPC and HTTP REST services.

- Most agents and probes use gRPC service for better performance and code readability.
- Some agents use REST service, because gRPC may be not supported in that language.
- The UI uses REST service, but the data is always in GraphQL format.


## Note
### IP binding
For users who are not familiar with IP binding, note that once IP binding is complete, the client could only use this IP to access the service. For example, if `172.09.13.28` is bound, even if you are
in this machine, you must use `172.09.13.28`, rather than `127.0.0.1` or `localhost`, to access the service.

### Module provider specified IP and port
The IP and port in the core module are provided by default. But it is common for some module providers, such as receiver modules, to provide other IP and port settings.


