# What is VNode?
On the trace page, you may sometimes find nodes with their spans named **VNode**, and that there are no attributes for such spans.

**VNode** is created by the UI itself, rather than being reported by the agent or tracing SDK. It indicates that some spans are missed in the trace data in this query.

## How does the UI detect the missing span(s)?
The UI checks the parent spans and reference segments of all spans in real time. If no parent id(segment id + span id) could be found,
then it creates a VNode automatically.

## How did this happen?
The VNode appears when the trace data is incomplete.
1. The agent fail-safe mechanism has been activated. The SkyWalking agent could abandon the trace data if there are any network issues between the agent and the OAP (e.g. failure to connect, slow network speeds, etc.), or if the OAP cluster is not capable of processing all traces. 
2. Some plug-ins may have bugs, and some segments in the trace do not stop correctly and are held in the memory.

In such case, the trace would not exist in the query, thus the VNode shows up. 
