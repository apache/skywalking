# Setting Override
## Supported version
5.0.0-beta + 

_Agent setting override supported since 3.2.5_

## What is setting override?
In default, SkyWalking provide `agent.config` for client, and `application.yml` for server settings. 

Setting override means end user can override the settings in these config file, by using system properties.

## Override priority
System.Properties(-D) > Config file
 
## Override
### Agent
Use `skywalking.` + key in config file as system properties and envs key, to override the value.

- Why need this prefix?

  The agent system properites and env share with target application, this prefix can avoid variable conflict.
  
### Collector
Use key in config file as system properties and envs key, to override the value.

Example:
- Setting in `application.yml`
```yaml
agent_gRPC:
  gRPC:
    host: localhost
    port: 11800
```

- Override port to 31200 by system property, add the following line into startup script.
```
-Dagent_gRPC.gRPC.port=31200
```