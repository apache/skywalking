## Spring annotation plugin
This plugin allows to trace all methods of beans in Spring context, which are annotated with
`@Bean`, `@Service`, `@Component` and `@Repository`.

- Why does this plugin optional?  

Tracing all methods in Spring context all creates a lot of spans, which also spend more CPU, memory and network.
Of course you want to have spans as many as possible, but please make sure your system payload can support these.
