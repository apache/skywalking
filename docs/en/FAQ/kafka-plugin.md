### Problem 
The trace doesn't continue in kafka consumer side.

### Reason
The kafka client is pulling message from server, the plugin also just traces the pull action. As that, you need to do the manual instrument before the pull action, and include the further data process.

### Resolve
Use Application Toolkit libraries to do manual instrumentation. such as `@KafkaPollAndInvoke` annotation at `apm-toolkit-kafka` or OpenTracing API, Or if you're using `spring-kafka` 2.2.x or above, you can track the Consumer side without any code change.
