# Service Auto Instrument Agent
The service auto instrument agent is a subset of language-based native agents. This kind of agents is based on
some language-specific features, especially those of a VM-based language. 

## What does Auto Instrument mean?
Many users learned about these agents when they first heard that "Not a single line of code has to be changed". SkyWalking used to mention this in its readme page as well.
However, this does not reflect the full picture. For end users, it is true that they no longer have to modify their codes in most cases.
But it is important to understand that the codes are in fact still modified by the agent, which is usually known as "runtime code manipulation". The underlying logic is that the
auto instrument agent uses the VM interface for code modification to dynamically add in the instrument code, such as modifying the class in Java through 
`javaagent premain`.

In fact, although the SkyWalking team has mentioned that most auto instrument agents are VM-based, you may build such tools during compiling time rather than
runtime.

## What are the limitations?
Auto instrument is very helpful, as you may perform auto instrument during compiling time, without having to depend on VM features. But there are also certain limitations that come with it:

- **Higher possibility of in-process propagation in many cases**. Many high-level languages, such as Java and .NET, are used for building business systems. 
 Most business logic codes run in the same thread for each request, which causes propagation to be based on thread ID, in order for the stack module to make sure that the context is safe.

- **Only works in certain frameworks or libraries**. Since the agents are responsible for modifying the codes during runtime, the codes are already known 
to the agent plugin developers. There is usually a list of frameworks or libraries supported by this kind of probes.
For example, see the [SkyWalking Java agent supported list](../setup/service-agent/java-agent/Supported-list.md).

- **Cross-thread operations are not always supported**. Like what is mentioned above regarding in-process propagation, most codes (especially business codes)
run in a single thread per request. But in some other cases, they operate across different threads, such as assigning tasks to other threads, task pools or batch processes. Some languages may even provide coroutine or similar components like `Goroutine`, which allows developers to run async process with low payload. In such cases, auto instrument will face problems. 

So, there's nothing mysterious about auto instrument. In short, agent developers write an activation script to make 
instrument codes work for you. That's it! 

## What is next?
If you want to learn about manual instrument libs in SkyWalking, see the [Manual instrument SDK](manual-sdk.md) section.

