# Setting Override
In default, SkyWalking provide `agent.config` for agent.

Setting override means end user can override the settings in these config file, through using system properties or agent options.


## System properties

Use `skywalking.` + key in config file as system properties key, to override the value.

- Why need this prefix?

  The agent system properties and env share with target application, this prefix can avoid variable conflict.

- Example

  Override `agent.application_code` by this.
```
-Dskywalking.agent.application_code=31200
```

## Agent options

Add the properties after the agent path in JVM arguments.

```
-javaagent:/path/to/skywalking-agent.jar=[option1]=[value1],[option2]=[value2]
```

- Example

  Override `agent.application_code` and `logging.level` by this.

```
-javaagent:/path/to/skywalking-agent.jar=agent.application_code=31200,logging.level=debug
```

- Special characters

  If a separator(`,` or `=`) in the option or value, it should be wrapped in quotes.

```
-javaagent:/path/to/skywalking-agent.jar=agent.ignore_suffix='.jpg,.jpeg'
```


## Override priority

Agent Options >  System.Properties(-D) > Config file
