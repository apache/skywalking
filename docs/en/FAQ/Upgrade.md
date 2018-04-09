### Version 5.0.0-alpha
#### Collector
**Problem**: 
All the registration information stored in the ElasticSearch, but all the metrics are missing.
So, there is no information showing in the UI.

**Reason**:
This problem cause of you use 3.2.6 version before, then you upgrade to 5.0.0-alpha but not recreate index. 
When service name registered, the es will create this column by default type string.

**Resolve**:
Clean the data folder in ElasticSearch and restart the collector and your application.
