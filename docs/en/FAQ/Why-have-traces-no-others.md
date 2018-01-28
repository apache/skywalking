**Problem**：
- There is no abnormal log in Agent log and Collector log, 
- The page can see the trace stack, but the others cannot see any data
- The top of the page Time line is inconsistent with the current system time

**Reason**：
The operating system where the monitored system is located is not set as the current time zone, causing statistics collection time points to deviate.

**Resolve**：
Set the time zone