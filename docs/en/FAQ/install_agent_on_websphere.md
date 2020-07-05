# IllegalStateException when install Java agent on WebSphere
This FAQ came from [community discussion and feedback](https://github.com/apache/skywalking/issues/2652). 
This user installed SkyWalking Java agent on WebSphere 7.0.0.11 and ibm jdk 1.8_20160719 and 1.7.0_20150407,
and had following error logs
```
WARN 2019-05-09 17:01:35:905 SkywalkingAgent-1-GRPCChannelManager-0 ProtectiveShieldMatcher : Byte-buddy occurs exception when match type.
java.lang.IllegalStateException: Cannot resolve type description for java.security.PrivilegedAction
at org.apache.skywalking.apm.dependencies.net.bytebuddy.pool.TypePool$Resolution$Illegal.resolve(TypePool.java:144)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.pool.TypePool$Default$WithLazyResolution$LazyTypeDescription.delegate(TypePool.java:1392)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.description.type.TypeDescription$AbstractBase$OfSimpleType$WithDelegation.getInterfaces(TypeDescription.java:8016)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.description.type.TypeDescription$Generic$OfNonGenericType.getInterfaces(TypeDescription.java:3621)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.HasSuperTypeMatcher.hasInterface(HasSuperTypeMatcher.java:53)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.HasSuperTypeMatcher.hasInterface(HasSuperTypeMatcher.java:54)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.HasSuperTypeMatcher.matches(HasSuperTypeMatcher.java:38)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.HasSuperTypeMatcher.matches(HasSuperTypeMatcher.java:15)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatcher$Junction$Conjunction.matches(ElementMatcher.java:107)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatcher$Junction$Disjunction.matches(ElementMatcher.java:147)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatcher$Junction$Disjunction.matches(ElementMatcher.java:147)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatcher$Junction$Disjunction.matches(ElementMatcher.java:147)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatcher$Junction$Disjunction.matches(ElementMatcher.java:147)
at org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatcher$Junction$Disjunction.matches(ElementMatcher.java:147)
...
```

The exception has been addressed as access grant required in WebSphere. 
You could follow these steps.

1. Set the agent's owner to the owner of WebSphere.
2. Add "grant codeBase "file:${agent_dir}/-" { permission java.security.AllPermission; };" in the file of "server.policy".

