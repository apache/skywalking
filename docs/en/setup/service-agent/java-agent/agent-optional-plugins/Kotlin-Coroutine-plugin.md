# Skywalking with Kotlin coroutine
This PR provided an auto instrument support plugin for Kotlin coroutine based on context snapshot.

## Description
We have known there are some limits with skywalking and coroutine, because of the trace context based on `ThreadLocal`.

But skywalking provided context snapshot for cross-thread tracing, I create this plugin for resolving context losing in Kotlin coroutine.

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
![Without kotlin plugin1](http://skywalking.apache.org/screenshots/7.0.0/kotlin/coroutine/Without coroutine plugin server1.jpg)
02. Server2 tracing path.
![Without kotlin plugin2](http://skywalking.apache.org/screenshots/7.0.0/kotlin/coroutine/Without coroutine plugin server2.jpg)

### Run with the plugin
With no business code changed, just install the plugin. We can find the tracing paths be connected together. We can get all info of one client call.

![With kotlin plugin](http://skywalking.apache.org/screenshots/7.0.0/kotlin/coroutine/Run with coroutine plugin.jpg)
