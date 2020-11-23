### Problem 
The trace doesn't continue in kafka consumer side.

### Reason
The kafka client is responsible for pulling messages from the brokers, and after that the data will be processed by the user-defined codes. However, only the poll action can be traced by the pluign and the subsequent data processing work is inevitably outside the scope of the trace context. Thus, in order to complete the client-side trace, manual instrument has to be done, i.e. the poll action and the processing action should be wrapped manually.

### Resolve
With native kafka client, please use Application Toolkit libraries to do the manual instrumentation, with the help of `@KafkaPollAndInvoke` annotation in `apm-toolkit-kafka` or with OpenTracing API. And if you're using `spring-kafka` 1.3.x, 2.2.x or above, you can track the Consumer side without effort.
