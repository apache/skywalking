### Problem 
When using a thread pool, `TraceSegment` data in a thread cannot be reported and there are memory data that cannot be recycled (memory leaks).

### Example
``` java
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadFactory(r -> new Thread(RunnableWrapper.of(r)));
```

### Reason

* Worker threads are enhanced when using the thread pool. 
* Based on the design of the SkyWalking Java Agent, when tracing a cross thread, you must enhance the task thread.

### Resolution

* When using `Thread Schedule Framework`:
See SkyWalking Thread Schedule Framework at [SkyWalking Java agent supported list](../setup/service-agent/java-agent/Supported-list.md), such as Spring FrameWork @Async, which can implement tracing without any modification. 

* When using `Custom Thread Pool`:
Enhance the task thread with the following code.

```java
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    executorService.execute(RunnableWrapper.of(new Runnable() {
        @Override public void run() {
            //your code
        }
    }));
```
See [across thread solution APIs](../setup/service-agent/java-agent/Application-toolkit-trace-cross-thread.md) for more use cases.

