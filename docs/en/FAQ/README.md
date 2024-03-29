# FAQs
These are known and frequently asked questions about SkyWalking. We welcome you to contribute here.

## Design
* [Why does SkyWalking use RPC(gRPC and RESTful) rather than MQ as transport layer by default?](why_mq_not_involved.md)
* [Why is Clickhouse or Loki or xxx not supported as a storage option?](why-clickhouse-not-supported.md)

## Compiling
* [Protoc plugin fails in maven build](Protoc-Plugin-Fails-When-Build.md)
* [Required items could not be found when importing project into Eclipse](Import-Project-Eclipse-RequireItems-Exception.md)
* [Maven compilation failure with error such as `python2 not found`](maven-compile-npm-failure.md)
* [Compiling issues on Mac's M1 chip](How-to-build-with-mac-m1.md)

## Runtime
* [New ElasticSearch storage option explanation in 9.2.0](New-ElasticSearch-storage-option-explanation-in-9.2.0.md)
* [Version 9.x+ upgrade](v9-version-upgrade.md)
* [Elasticsearch exception `type=version_conflict_engine_exception` since 8.7.0](es-version-conflict.md)
* [Version 8.x+ upgrade](v8-version-upgrade.md)
* [Why do metrics indexes with Hour and Day precisions stop updating after upgrade to 7.x?](Hour-Day-Metrics-Stopping.md)
* [Version 6.x upgrade](v6-version-upgrade.md)
* [Why are there only traces in UI?](Why-have-traces-no-others.md)
* [Tracing doesn't work on the Kafka consumer end](kafka-plugin.md)
* [Agent or collector version upgrade,  3.x -> 5.0.0-alpha](v3-version-upgrade.md)
* [EnhanceRequireObjectCache class cast exception](EnhanceRequireObjectCache-Cast-Exception.md)
* [ElasticSearch server performance issues, including ERROR CODE:429](ES-Server-FAQ.md)
* [IllegalStateException when installing Java agent on WebSphere 7](install_agent_on_websphere.md)
* ["FORBIDDEN/12/index read-only / allow delete (api)" appears in the log](https://discuss.elastic.co/t/forbidden-12-index-read-only-allow-delete-api/110282)
* [No data shown and backend replies with "Variable 'serviceId' has coerced Null value for NonNull type 'ID!'"](time-and-timezone.md)
* [**Unexpected endpoint register** warning after 6.6.0](Unexpected-endpoint-register.md)
* [Use the profile exporter tool if the profile analysis is not right](../guides/backend-profile-export.md)
* [Compatibility with other javaagent bytecode processes](Compatible-with-other-javaagent-bytecode-processing.md)
* [**Java agent memory leak** when enhancing `Worker thread` at Thread Pool](Memory-leak-enhance-Worker-thread.md)
* [Thrift plugin](thrift-plugin.md)

## UI
* [What is **VNode**? And why does SkyWalking have that?](vnode.md)
