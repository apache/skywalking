## Version 3.x -> 5.0.0-alpha Upgrade FAQs
### Collector
### Problem
There is no information showing in the UI.

### Cause
In the upgrade from version 3.2.6 to 5.0.0, the existing Elasticsearch indexes are kept, but aren't compatible with 5.0.0-alpha.
When service name is registered, ElasticSearch will create this column by default type string, which will lead to an error.

### Solution
Clean the data folder in ElasticSearch and restart ElasticSearch, collector and your application under monitoring.
