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

## System environment variables
- Example

  Override `agent.application_code` and `logging.level` by this.

```
# The service name in UI
agent.service_name=${SW_AGENT_NAME:Your_ApplicationName}

# Logging level
logging.level=${SW_LOGGING_LEVEL:INFO}
```

If the `SW_AGENT_NAME ` environment variable exists in your operating system and its value is `skywalking-agent-demo`, 
then the value of `agent.service_name` here will be overwritten to `skywalking-agent-demo`, otherwise, it will be set to `Your_ApplicationName`.

By the way, Placeholder nesting is also supported, like `${SW_AGENT_NAME:${ANOTHER_AGENT_NAME:Your_ApplicationName}}`.
In this case, if the `SW_AGENT_NAME ` environment variable not exists, but the ```ANOTHER_AGENT_NAME``` 
environment variable exists and its value is `skywalking-agent-demo`, then the value of `agent.service_name` here will be overwritten to `skywalking-agent-demo`,otherwise, it will be set to `Your_ApplicationName`.


## Override priority

Agent Options > System.Properties(-D) > System environment variables > Config file
