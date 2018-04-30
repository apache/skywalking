# Optional plugins
Optional plugins could be provided by source codes or in `optional-plugins` folder under agent.

For using these plugins, you need to compile source codes by yourself, or copy the certain plugins to `/plugins`.

## Spring bean plugin
This plugin allows to trace all methods of beans in Spring context, which are annotated with
`@Bean`, `@Service`, `@Component` and `@Repository`.

- Why does this plugin optional?  
Tracing all methods in Spring context all creates a lot of spans, which also spend more CPU, memory and network.
Of course you want to have spans as many as possible, but please make sure your system payload can support these.

## Oracle and Resin plugins
These plugins can't be provided in Apache release because of Oracle and Resin Licenses.
If you want to know details, please read [Apache license legal document](https://www.apache.org/legal/resolved.html)

- How should we build these optional plugins in local?

1. Resin 3: Download Resin 3.0.9 and place the jar at `/ci-dependencies/resin-3.0.9.jar`.
1. Resin 4: Download Resin 4.0.41 and place the jar at `/ci-dependencies/resin-4.0.41.jar`.
1. Oracle: Download Oracle OJDBC-14 Driver 10.2.0.4.0 and place the jar at `/ci-dependencies/ojdbc14-10.2.0.4.0.jar`.


