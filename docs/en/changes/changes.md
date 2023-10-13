## 9.7.0

#### Project


#### OAP Server

* ElasticSearchClient: Add `deleteById` API.
* Fix Custom alarm rules are overwritten by 'resource/alarm-settings.yml'
* Support Kafka Monitoring.
* [Breaking Change] Elasticsearch storage merge all management data indices into one index `management`, 
  including `ui_template，ui_menu，continuous_profiling_policy`.
* Add a release mechanism for alarm windows when it is expired in case of OOM.
* Support collect ZGC memory pool metrics.

#### UI



#### Documentation

* Separate storage docs to different files, and add an estimated timeline for BanyanDB(end of 2023). 
* Add topology configuration in UI-Grafana doc.


All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/193?closed=1)
