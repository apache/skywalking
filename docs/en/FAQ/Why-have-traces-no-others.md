### Problem
- There is no abnormal log in Agent log and Collector log, 
- The traces show, but no other info in UI.

### Reason
The operating system where the monitored system is located is not set as the current time zone, causing statistics collection time points to deviate.

### Resolve
Make sure the time is sync in collector servers and monitored application servers.
