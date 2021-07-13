### Problem
- There is no abnormal log in Agent log and Collector log.
- The traces can be seen, but no other information is available in UI.

### Reason
The operating system where the monitored system is located is not set as the current time zone, causing statistics collection time points to deviate.

### Resolution
Make sure the time is synchronized between collector servers and monitored application servers.
