### Problem 
The trace doesn't continue in kafka consumer side.

### Reason
The kafka client is pulling message from server, the plugin also just traces the pull action. As that, you need to do the manual instrument before the pull action, and include the further data process.

### Resolve
Use Application Toolkit libraries to do manual instrumentation. such as `@Trace` annotation or OpenTracing API.
