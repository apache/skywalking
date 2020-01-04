# Skywalking with Kotlin coroutine
This Plugin provides an auto instrument support plugin for Kotlin coroutine based on context snapshot.

## Description
SkyWalking provide tracing context propagation inside thread. In order to support Kotlin Coroutine, we provide this additional plugin.

## Implementation principle
As we know, Kotlin coroutine switches the execution thread by `CoroutineDispatcher`.

01. Create a snapshot of the current context before dispatch the continuation.
02. Then create a coroutine span after thread switched, mark the span continued with the snapshot.
03. Every new span which created in the new thread will be a child of this coroutine span. So we can link those span together in a tracing.
04. After the original runnable executed, we need to stop the coroutine span for cleaning thread state.

## Some screenshots
### Run without the plugin
We run a Kotlin coroutine based gRPC server without this coroutine plugin.  
You can find, the one call (client -> server1 -> server2) has been split two tracing paths.

01. Server1 without exit span and server2 tracing path.
![Without kotlin plugin1](http://skywalking.apache.org/screenshots/7.0.0/kotlin/coroutine/without-coroutine-plugin-server1.jpg)
02. Server2 tracing path.
![Without kotlin plugin2](http://skywalking.apache.org/screenshots/7.0.0/kotlin/coroutine/without-coroutine-plugin-server2.jpg)

### Run with the plugin
Without changing codes manually, just install the plugin. We can find the spans be connected together. We can get all info of one client call.

![With kotlin plugin](http://skywalking.apache.org/screenshots/7.0.0/kotlin/coroutine/run-with-coroutine-plugin.jpg)
