# Dynamical Logging

The OAP server leverages `log4j2` to manage the logging system. `log4j2` supports changing the configuration 
at runtime, but you have to manually update the XML configuration file, which could be time-consuming and prone to man-made mistakes.

Dynamical logging, which depends on dynamic configuration, provides us with an agile way to update all OAP `log4j` 
configurations through a single operation.

The key of the configuration item is `core.default.log4j-xml`, and you can select any of the configuration implements 
to store the content of `log4j.xml`. In the booting phase, once the core module gets started, `core.default.log4j-xml`
would come into the OAP log4j context.

If the configuration is changed after the OAP startup, you have to wait for a while for the changes to be applied. The default value is `60` seconds, which you could change through `configuration.<configuration implement>.period` in `application.yaml`.

If you remove `core.default.log4j-xml` from the configuration center or disable the configuration module, `log4j.xml` in the `config` directory would be affected.

> Caveat: The OAP only supports the XML configuration format.

This is an example of configuring dynamical logging through a ConfigMap in a Kubernetes cluster. You may set up other configuration
clusters following the same procedures.

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
