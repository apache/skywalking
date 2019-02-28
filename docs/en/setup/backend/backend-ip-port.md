# IP and port setting
Backend is using IP and port binding, in order to support the OS having multiple IPs.
The binding/listening IP and port are specified by core module
```yaml
core:
  default:
    restHost: 0.0.0.0
    restPort: 12800
    restContextPath: /
    gRPCHost: 0.0.0.0
    gRPCPort: 11800
```
There are two IP/port pair for gRPC and HTTP rest services.

- Most agents and probes use gRPC service for better performance and code readability.
- Few agent use rest service, because gRPC may be not supported in that language.
- UI uses rest service, but data in GraphQL format, always.


## Notice
### IP binding
In case some users are not familiar with IP binding, you should know, after you did that, 
the client could only use this IP to access the service. For example, bind `172.09.13.28`, even you are
in this machine, must use `172.09.13.28` rather than `127.0.0.1` or `localhost` to access the service.

### Module provider specified IP and port
The IP and port in core are only default provided by core. But some module provider may provide other
IP and port settings, this is common. Such as many receiver modules provide this.


