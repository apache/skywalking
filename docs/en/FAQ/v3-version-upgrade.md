## Version 3.x -> 5.0.0-alpha Upgrade FAQs
### Collector
### Problem
There is no information showing in the UI.

### Cause
In upgrate from 3.2.6 to 5.0.0, Elasticsearch indexes aren't recreated, because not indexes exist, but aren't compatible with 5.0.0-alpha.
When service name registered, the es will create this column by default type string, which is wrong.

### Solution
Clean the data folder in ElasticSearch and restart ElasticSearch, collector and your under monitoring application.
