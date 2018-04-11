# 跨线程追踪
* 使用 maven 和 gradle 依赖相应的工具包
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-trace</artifactId>
      <version>${skywalking.version}</version>
   </dependency>
```

* 使用方式一.
```java
    @TraceCrossThread
    public static class MyCallable<String> implements Callable<String> {
        @Override
        public String call() throws Exception {
            return null;
        }
    }
...
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    executorService.submit(new MyCallable());
```
* 使用方式二.
```java
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    executorService.submit(CallableWrapper.of(new Callable<String>() {
        @Override public String call() throws Exception {
            return null;
        }
    }));
```
_示例代码，仅供参考_

##跨线程追踪插件 说明
* 父线程没有任何追踪信息,第一次访问子线程之前会生成LocalSpan,之后访问的子线程都生成一个LocalSpan
* 父线程如果存在追踪信息,访问子线程都生成LocalSpan



