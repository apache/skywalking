## Spring bean 插件
这个插件允许在Spring上下文中追踪带有`@Bean`、 `@Service`、`@Component`和`@Repository`注解的bean的所有方法。

- 为什么这个插件是可选的？

在Spring上下文中追踪所有方法会创建很多的span，也会消耗更多的CPU，内存和网络。  
当然你希望包含尽可能多的span，但请确保你的系统有效负载能够支持这些。
