# What is VNode?
In the trace page, sometimes, people could find there are nodes named **VNode** as the span name, and there is no attribute 
for this span.

**VNode** is created by the UI itself, rather than reported from the agent or tracing SDK. It represents there are some
span(s) missed from the trace data in this query.

## How does the UI detect the missing span(s)?
The UI real check the parent spans and reference segments of all spans, if a parent id(segment id + span id) can't be found,
then it creates a VNode automatically.

## How does this happen?
The VNode was introduced, because there are some cases which could cause the trace data are not always completed.
1. The agent fail-safe mechanism activated. The SkyWalking agent has the capability to abandon the trace data, if
there is agent->OAP network issue(unconnected, slow network speed), or the performance of the OAP cluster is not enough
to process all traces. 
1. Some plugins could have bugs, then some segments in the trace never stop correctly, it is hold in the memory.

In these cases, the trace would not exist in the query. Then VNode shows up. 
