# What's sky-walking application toolkit?
Sky-walking application toolkit is a batch of libraries, provided by skywalking APM. Using them, you have a bridge between your application and skywalking APM agent. 

_**Most important**_, they will not trigger any runtime or performance issues for your application, whether skywalking tracer is active or not. 

# What does bridge mean?
As you known, skywalking agent run by -javeagent VM parameter. So you definitely don't need to change even a single line of your codes. But in some cases, you want to do interop with tracing/APM system. This is the moment you want to use application toolkit. 
e.g.
1. Integrate trace context(traceId) into your log component, e.g. log4j, log4j2 and logback. 
1. Use CNCF OpenTracing for manually instrumentation. 
1. Use Skywalking annotation and interactive APIs. 


_**Notice**: all toolkits libraries are on bitray.com/jcenter. And make sure their version should be as same as the tracer's version._