# Setting Override
In default, SkyWalking provide `agent.config` for agent 

Setting override means end user can override the settings in these config file, through using system properties.
 

Use `skywalking.` + key in config file as system properties key, to override the value.

- Why need this prefix?

  The agent system properites and env share with target application, this prefix can avoid variable conflict.

- Override priority

  System.Properties(-D) > Config file  

- Example

  Override `agent.application_code` by this.
```
-Dskywalking.agent.application_code=31200
```