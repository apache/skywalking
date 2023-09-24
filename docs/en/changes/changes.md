## 9.7.0

#### Project

* Add distribution/graal module to make preliminary preparations for supporting native-image.

#### OAP Server

* ElasticSearchClient: Add `deleteById` API.
* Fix Custom alarm rules are overwritten by 'resource/alarm-settings.yml'
* Support Kafka Monitoring.
* [Breaking Change] Elasticsearch storage merge all management data indices into one index `management`, 
  including `ui_template，ui_menu，continuous_profiling_policy`.
* Add a release mechanism for alarm windows when it is expired in case of OOM.
* Apply MQE on K8s layer UI-templates

#### UI

* Add new menu for kafka monitoring.
* Fix independent widget duration.
* Fix the display height of the link tree structure.
* Replace the name by shortName on service widget.
* Refactor: update pagination style. No visualization style change.

#### Documentation

* Separate storage docs to different files, and add an estimated timeline for BanyanDB(end of 2023). 
* Add topology configuration in UI-Grafana doc.


All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/193?closed=1)
