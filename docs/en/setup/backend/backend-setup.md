# Backend setup
First and most important thing is, SkyWalking backend startup behaviours are driven by `config/application.yml`.
Understood the setting file will help you to read this document.

## application.yml
The core concept behind this setting file is, SkyWalking collector is based on pure modulization design. 
End user can switch or assemble the collector features by their own requirements.

So, in `application.yml`, there are three levels.
1. **Level 1**, module name. Meaning this module is active in running mode.
1. **Level 2**, provider name. Set the provider of the module.
1. **Level 3**. settings of the provider.

Example:
```yaml
core:
  default:
    restHost: 0.0.0.0
    restPort: 12800
    restContextPath: /
    gRPCHost: 0.0.0.0
    gRPCPort: 11800
```
1. **core** is the module.
1. **default** is the default implementor of core module.
1. `restHost`, `restPort`, ... `gRPCHost` are all setting items of the implementor.

At the same time, modules includes required and optional, the required modules provide the skeleton of backend,
even modulization supported pluggable, remove those modules are meanless. We highly recommend you don't try to
switch those modules, unless you are well known SkyWalking project and its codes.

List the required modules here
1. **core**
1. **cluster**
1. **storage**
1. **query**

After understand the setting file structure, you could choose your interesting feature document.
We recommend you to read the feature documents in our following list.

1. [IP and port setting](backend-ip-port.md). Introduce how IP and port set and be used.
1. [Cluster management](backend-cluster.md). Guide you to set backend server in cluster mode.
1. [Deploy in kubernetes](backend-k8s.md). Guide you to build and use SkyWalking image, and deploy in k8s.
