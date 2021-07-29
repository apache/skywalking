# Dynamical Logging

OAP server leverage the log4j2 to manage the logging system. The log4j2 supports changing the configuration 
at the runtime, but you have to update the XML configuration file manually,  which could waste you much time and 
easy to make mistakes.

The dynamical logging, which depends on the dynamic configuration, provides us an agile way to update all OAP log4j 
configurations through a single operation.

The key of the configuration item is `core.default.log4j-xml`, and you can select any one of the configuration implements 
to store the content of log4j.xml. In the booting phase, once the core module gets started, the `core.default.log4j-xml`
would come into the OAP log4j context.

Supposing changing the configuration after OAP started, you have to wait a while to get the changes applied. 
The default value is `60` seconds which you could change through `configuration.<configuration implement>.peroid` in application.yaml.

If you remove `core.default.log4j-xml` from the configuration center or disable the configuration module, the `log4j.xml`
laid in `config` directory would get the effect. 

> Caveat: OAP only supports XML configuration format.

There is an example to show how to config dynamical logging through a ConfigMap in a Kubernetes cluster. Other configuration
clusters could follow the same convention to set up.

```yaml
apiVersion: v1
data:
  core.default.log4j-xml: |-
    <Configuration status="WARN">
       <Appenders>
         <Console name="Console" target="SYSTEM_OUT">
           <PatternLayout charset="UTF-8" pattern="%d - %c - %L [%t] %-5p %x - %m%n"/>
         </Console>
       </Appenders>
       <Loggers>
         <logger name="io.grpc.netty" level="INFO"/>
         <logger name="org.apache.skywalking.oap.server.configuration.api" level="TRACE"/>
         <logger name="org.apache.skywalking.oap.server.configuration.configmap" level="DEBUG"/>
         <Root level="WARN">
           <AppenderRef ref="Console"/>
         </Root>
        </Loggers>
    </Configuration>
kind: ConfigMap
metadata:
  labels:
    app: collector
    release: skywalking
  name: skywalking-oap
  namespace: default
```

