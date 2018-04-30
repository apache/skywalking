# 可选插件
可选插件可以由源代码或者agent下的`optional-plugins`文件夹中提供。

为了使用这些插件，你需要自己编译源代码，或将某些插件复制到`/plugins`。

## Spring bean 插件
这个插件允许在Spring上下文中追踪带有`@Bean`、 `@Service`、`@Component`和`@Repository`注解的bean的所有方法。

- 为什么这个插件是可选的？
在Spring上下文中追踪所有方法会创建很多的span，也会消耗更多的CPU，内存和网络。  
当然你希望包含尽可能多的span，但请确保你的系统有效负载能够支持这些。

## Oracle and Resin 插件
由于Oracle和Resin的License，这些插件无法在Apache发行版中提供。  
如果你想了解详细信息，请阅读 [Apache license legal document](https://www.apache.org/legal/resolved.html)

- 我们应该如何在本地构建这些可选插件？

1. Resin 3: 下载Resin 3.0.9 并且把jar放在`/ci-dependencies/resin-3.0.9.jar`.
1. Resin 4: 下载Resin 4.0.41 并且把jar放在`/ci-dependencies/resin-4.0.41.jar`.
1. Oracle: 下载Oracle OJDBC-14 Driver 10.2.0.4.0 并且把jar放在`/ci-dependencies/ojdbc14-10.2.0.4.0.jar`.
