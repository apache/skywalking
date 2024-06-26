## 10.1.0

#### Project
* E2E: bump up the version of the opentelemetry-collector to 0.102.1.

#### OAP Server

* Fix wrong indices in the eBPF Profiling related models.
* Support exclude the specific namespaces traffic in the eBPF Access Log receiver.
* Add Golang as a supported language for Elasticsearch.
* Remove unnecessary BanyanDB flushing logs(info).
* Increase `SW_CORE_GRPC_MAX_MESSAGE_SIZE` to 50MB.
* Support to query relation metrics through PromQL.
* Support trace MQE query for debugging.
* Add Component ID(158) for the Solon framework.
* Fix metrics tag in HTTP handler of browser receiver plugin.
* Increase `alarm_record#message` column length to 2000 from 200.
* Remove `alarm_record#message` column indexing.
* Add Python as a supported language for Pulsar.
* Make more proper histogram buckets for the `persistence_timer_bulk_prepare_latency`,
  `persistence_timer_bulk_execute_latency` and `persistence_timer_bulk_all_latency` metrics in PersistenceTimer.
* [Break Change] Update Nacos version to 2.3.2.

#### UI


#### Documentation

* Update the version description supported by zabbix receiver.
* Move the Official Dashboard docs to marketplace docs. 
* Add marketplace introduction docs under `quick start` menu to reduce the confusion of finding feature docs.
* Update Windows Metrics(Swap -> Virtual Memory)
* [Break Change] Update Nacos version to 2.3.2.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/205?closed=1)
