## Download skywalking agent release version
- Go to [release page](http://skywalking.apache.org/downloads/)

## Install skywalking javaagent
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

- Start your application.

# Advanced features
- All plugins are in `/plugins` folder. The plugin jar is active when it is in there. Remove the plugin jar, it disabled.
- The default logging output folder is `/logs`.

# Install javaagent FAQs
- Linux Tomcat 7, Tomcat 8  
Change the first line of `tomcat/bin/catalina.sh`.
```shell
CATALINA_OPTS="$CATALINA_OPTS -javaagent:/path/to/skywalking-agent/skywalking-agent.jar"; export CATALINA_OPTS
```

- Windows Tomcat 7, Tomcat 8  
Change the first line of `tomcat/bin/catalina.bat`.
```shell
set "CATALINA_OPTS=-javaagent:/path/to/skywalking-agent/skywalking-agent.jar"
```
- JAR file  
Add `-javaagent` argument to command line in which you start your app. And make sure to add it before the `-jar` argument. eg:
 ```shell
 java -javaagent:/path/to/skywalking-agent/skywalking-agent.jar -jar yourApp.jar
 ```
