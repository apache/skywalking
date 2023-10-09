## 9.7.0

#### Project

* Add distribution/graal module to make preliminary preparations for supporting native-image.
* Bump Java agent to 9.1-dev in the e2e tests.
* Bump up netty to 4.1.99.

#### OAP Server

* ElasticSearchClient: Add `deleteById` API.
* Fix Custom alarm rules are overwritten by 'resource/alarm-settings.yml'
* Support Kafka Monitoring.
* [Breaking Change] Elasticsearch storage merge all management data indices into one index `management`, 
  including `ui_template，ui_menu，continuous_profiling_policy`.
* Add a release mechanism for alarm windows when it is expired in case of OOM.
* Fix Zipkin trace receiver response: make the HTTP status code from `200` to `202`.
* Update BanyanDB Java Client to 0.5.0.
* Fix getInstances query in the BanyanDB Metadata DAO.
* BanyanDBStorageClient: Add `keepAliveProperty` API.

#### UI

* Add new menu for kafka monitoring.
* Fix independent widget duration.
* Fix the display height of the link tree structure.
* Replace the name by shortName on service widget.
* Refactor: update pagination style. No visualization style change.
* Apply MQE on K8s layer UI-templates.
* Fix icons display in trace tree diagram.

#### Documentation

* Separate storage docs to different files, and add an estimated timeline for BanyanDB(end of 2023). 
* Add topology configuration in UI-Grafana doc.


All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/193?closed=1)
