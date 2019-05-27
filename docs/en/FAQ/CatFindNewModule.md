### Problem
when you want add a new module in SW, you will encounter a error which is
```java
2019-05-27 10:44:31,249 - org.apache.skywalking.oap.server.starter.OAPServerStartUp - 55 [main] ERROR [] - [test, receiver-register] missing.
org.apache.skywalking.oap.server.library.module.ModuleNotFoundException: [test] missing.
	at org.apache.skywalking.oap.server.library.module.ModuleManager.init(ModuleManager.java:60) ~[classes/:?]
	at org.apache.skywalking.oap.server.starter.OAPServerStartUp.main(OAPServerStartUp.java:43) [classes/:?]
...
```

### Reason
this exception may caused by the classloader can't find the new module which is extends from ModuleDefine in the classpath.
### Resolve 
1. add a new file in main\resources\META-INF\services, which name is org.apache.skywalking.oap.server.library.module.ModuleDefine, and edit the file.
2. you should add you new moule to the "server-starter" project. it will be like this(in the pom.xml file)
```java
        <dependency>
            <groupId>org.apache.skywalking</groupId>
            <artifactId>test</artifactId>
            <version>${project.version}</version>
        </dependency>
```
