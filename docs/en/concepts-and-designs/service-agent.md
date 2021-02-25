# Service Auto Instrument Agent
Service auto instrument agent is a subset of Language based native agents. In this kind of agent, it is based on
some language specific features, usually a VM based languages. 

## What does Auto Instrument mean?
Many users know these agents from hearing
`They say don't need to change any single line of codes`, SkyWalking used to put these words in our readme page too.
But actually, it is right and wrong. For end user, **YES**, they don't need to change codes, at least for most cases.
But also **NO**, the codes are still changed by agent, usually called `manipulate codes at runtime`. Underlying, it is just
auto instrument agent including codes about how to change codes through VM interface, such as change class in Java through 
`javaagent premain`.

Also, we said that the most auto instrument agents are VM based, but actually, you can build a tool at compiling time, rather than 
runtime.

## What are limits?
Auto instrument is so cool, also you can create those in compiling time, that you don't depend on VM features, then is there
any limit?

The answer is definitely **YES**. And they are:
- **In process propagation possible in most cases**. In many high level languages, they are used to build business system, 
such as Java and .NET. Most codes of business logic are running in the same thread for per request, which make the propagation 
could be based on thread Id, and stack module to make sure the context is safe.

- **Just effect frameworks or libraries**. Because of the changing codes by agents, it also means the codes are already known 
by agent plugin developers. So, there is always a supported list in this kind of probes.
Like [SkyWalking Java agent supported list](../setup/service-agent/java-agent/Supported-list.md).

- **Across thread can't be supported all the time**. Like we said about **in process propagation**, most codes
run in a single thread per request, especially business codes. But in some other scenarios, they do things in different threads, such as 
job assignment, task pool or batch process. Or some languages provide coroutine or similar thing like `Goroutine`, then 
developer could run async process with low payload, even been encouraged. In those cases, auto instrument will face problems. 

So, no mystery for auto instrument, in short words, agent developers write an activation to make 
instrument codes work you. That is all. 

## What is next?
If you want to learn about manual instrument libs in SkyWalking, see [Manual instrument SDK](manual-sdk.md) section.

