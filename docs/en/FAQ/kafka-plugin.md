### Problem 
Tracing doesn't work on the Kafka consumer end.

### Reason
The kafka client is responsible for pulling messages from the brokers, after which the data will be processed by user-defined codes. However, only the poll action can be traced by the plug-in and the subsequent data processing work inevitably goes beyond the scope of the trace context. Thus, in order to complete tracing on the client end, manual instrumentation is required, i.e. the poll action and the processing action should be wrapped manually.

### Resolve
For a native Kafka client, please use the Application Toolkit libraries to do the manual instrumentation, with the help of the `@KafkaPollAndInvoke` annotation in `apm-toolkit-kafka` or with OpenTracing API. If you're using `spring-kafka` 1.3.x, 2.2.x or above, you can easily trace the consumer end without further configuration.
