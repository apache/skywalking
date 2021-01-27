# Load outside jar to classpath 
 
### Problem
In some cases, especially when the target java app is packaged in an old way,that mean the depency libs directory is out of the target application jar. We need to add some VM options to make sure the java applications starts well, like "java -cp **/lib -ext.dirs **/lib" .
But when we add these VM options, the target ClassLoaders may not be able to load any classes from skywalking-agent.jar cause it's out of the path i have defined previously. The target app classloader or any of its parent classloader will load the class with failure and push it down to AgentClassLoader to load the class ,and finally ,the not class found exception will stop the application from start.

### Resolve
"-Xbootclasspath/a:" can solve this problem.
By the way ,we can use the BootstrapClassLoader to load the them.
We can run the "oldWay.jar" such as:
```text
java -javaagent:/{skywalking-agent-dir}/skywalking-agent-abm.jar
-Xbootclasspath/a:/{your}.jar
-Dskywalking.agent.service_name=serverName
-Dskywalking.collector.backend_service=127.0.0.1:11800
```

Maybe  multiple  jar  in one directory:
```text
java -javaagent:/{skywalking-agent-dir}/skywalking-agent-abm.jar
-Xbootclasspath/a:/{jar-directory}
-Dskywalking.agent.service_name=serverName
-Dskywalking.collector.backend_service=127.0.0.1:11800
```
