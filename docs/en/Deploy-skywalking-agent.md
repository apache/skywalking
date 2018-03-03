## Download skywalking agent release version
- Go to [release page](https://github.com/apache/incubator-skywalking/releases)

## Deploy skywalking javaagent
1. Copy the agent package to anywhere you like. The logs, plugins and config are all included in the package.
2. Add -javaagent:/path/to/skywalking-agent/skywalking-agent.jar to VM argument. 

New agent package looks like this：
```
+-- skywalking-agent
    +-- activations
         apm-toolkit-log4j-1.x-activation.jar
         apm-toolkit-log4j-2.x-activation.jar
         apm-toolkit-logback-1.x-activation.jar
         ...
    +-- config
         agent.config  
    +-- plugins
         apm-dubbo-plugin.jar
         apm-feign-default-http-9.x.jar
         apm-httpClient-4.x-plugin.jar
         .....
    skywalking-agent.jar
```

- Start your application。

# Advanced features
- All plugins are in `/plugin` folder. The plugin jar is active when it is in there. Remove the plugin jar, it disabled.
- Besides set config through `/config/agent.config`, you can use System.Env and System.Properties(-D) to set config.
  - Key of env and properties = `skywalking.` + key in `agent.config` file
  - Priority: System.Env > System.Properties(-D) > `/config/agent.config`
- The default logging output folder is `/log`.

# Deploy agent in Tomcat FAQ
- Tomcat 7
Change the first line of `tomcat/bin/catalina.sh`.
```shell
CATALINA_OPTS="$CATALINA_OPTS -javaagent:/path/to/skywalking-agent/skywalking-agent.jar"; export CATALINA_OPTS
```

- Tomcat 8
Change the first line of `tomcat/bin/catalina.sh`.
```shell
set "CATALINA_OPTS=-javaagent:E:\apache-tomcat-8.5.20\skywalking-agent\skywalking-agent.jar"
```