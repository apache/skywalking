### Problem 
When using a thread pool, `TraceSegment` data in a thread cannot be reported and there are memory data that cannot be recycled (memory leaks)

### Example
``` java
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadFactory(r -> new Thread(RunnableWrapper.of(r)));
```

### Reason

* Worker threads are enhanced, when using thread pool. 
* According to the SkyWalking Java Agent design, when you want to trace cross thread, you need to enhance the task thread.

### Resolve

* When using `Thread Schedule Framework`
Checked SkyWalking Thread Schedule Framework at [SkyWalking Java agent supported list](../setup/service-agent/java-agent/Supported-list.md), such as Spring FrameWork @Async, which can implement tracing without any modification. 

* When using `Custom Thread Pool`
Enhance the task thread with the following usage.

```java
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    executorService.execute(RunnableWrapper.of(new Runnable() {
        @Override public void run() {
            //your code
        }
    }));
```
See [across thread solution APIs](../setup/service-agent/java-agent/Application-toolkit-trace-cross-thread.md) for more usage

