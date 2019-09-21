# Why doesn't SkyWalking involve MQ in the architecture?
People usually ask about these questions when they know SkyWalking at the first time.
They think MQ should be better in the performance and supporting high throughput, like the following

<img src="MQ-involved-architecture.png"/>

Here are the reasons the SkyWalking's opinions.

### Is MQ a good or right way to communicate with OAP backend?
This question comes out when people think about what happens when the OAP cluster is not powerful enough or offline. 
But I want to ask the questions before answer this.
1. Why do you think OAP should be not powerful enough? As it is not, the speed of data analysis wouldn't catch up with producers(agents). Then what is the point of adding new deployment requirement?
1. Maybe you will argue says, the payload is sometimes higher than usual as there is hot business time. But, my question is how much higher? 
1. If less than 40%, how many resources will you use for the new MQ cluster? How about moving them to new OAP and ES nodes?
1. If higher than 40%, such as 70%-2x times? Then, I could say, your MQ wastes more resources than it saves. 
Your MQ would support 2x-3x payload, and with 10%-20% cost in general time. Furthermore, in this case, 
if the payload/throughput are so high, how long the OAP cluster could catch up. I would say never before it catches up, the next hot time event is coming.

Besides all this analysis, why do you want the traces still 100%, as you are costing so many resources? 
Better than this, 
we could consider adding a better dynamic trace sampling mechanism at the backend, 
when throughput goes over the threshold, active the sampling rate to 100%->10% step by step, 
which means you could get the OAP and ES 3 times more powerful than usual, just ignore the traces at hot time.

### How about MQ metrics data exporter?
I would say, it is already available there. Exporter module with gRPC default mechanism is there. It is easy to provide a new implementor of that module.