## 9.1.0

#### Project

* Upgrade zipkin to 2.23.16.

#### OAP Server

* Add component definition(ID=127) for `Apache ShenYu (incubating)`.
* Fix Zipkin receiver: Decode spans error, missing `Layer` for V9 and wrong time bucket for generate Service and
  Endpoint.
* [Refactor] Move SQLDatabase(H2/MySQL/PostgreSQL), ElasticSearch and BanyanDB specific configurations out of column.
* Support BanyanDB global index for entities. Log and Segment record entities declare this new feature.
* Remove unnecessary analyzer settings in columns of templates. Many were added due to analyzer's default value.
* Simplify the Kafka Fetch configuration in cluster mode.
* [Breaking Change] Update the eBPF Profiling task to the service level,
  please delete index/table: `ebpf_profiling_task`, `process_traffic`.
* Fix event can't split service ID into 2 parts.
* Fix OAP Self-Observability metric `GC Time` calculation.
* Set `SW_QUERY_MAX_QUERY_COMPLEXITY` default value to `1000`
* Webapp module (for UI) enabled compression.
* [Breaking Change] Add layer field to event, report an event without layer is not allowed.
* Fix ES flush thread will stop when flush schedule task have exception.

#### UI

* General service instance: move `Thread Pool` from JVM to Overview, fix `JVM GC Count` calculation.
* Add Apache ShenYu (incubating) component LOGO.
* Show more metrics on service/instance/endpoint list on the dashboards.
* Support average values of metrics on the service/list/endpoint table widgets, with pop-up linear graph

#### Documentation

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/128?closed=1)
