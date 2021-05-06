# Extend storage
SkyWalking has already provided several storage solutions. In this document, you could 
learn how to easily implement a new storage. 

## Define your storage provider
1. Define class extension `org.apache.skywalking.oap.server.library.module.ModuleProvider`.
2. Set this provider targeting to storage module.
```java
@Override 
public Class<? extends ModuleDefine> module() {
    return StorageModule.class;
}
```

## Implement all DAOs
Here's a list of all DAO interfaces in storage:
1. IServiceInventoryCacheDAO
1. IServiceInstanceInventoryCacheDAO
1. IEndpointInventoryCacheDAO
1. INetworkAddressInventoryCacheDAO
1. IBatchDAO
1. StorageDAO
1. IRegisterLockDAO
1. ITopologyQueryDAO
1. IMetricsQueryDAO
1. ITraceQueryDAO
1. IMetadataQueryDAO
1. IAggregationQueryDAO
1. IAlarmQueryDAO
1. IHistoryDeleteDAO
1. IMetricsDAO
1. IRecordDAO
1. IRegisterDAO
1. ILogQueryDAO
1. ITopNRecordsQueryDAO
1. IBrowserLogQueryDAO

1. IProfileTaskQueryDAO
1. IProfileTaskLogQueryDAO
1. IProfileThreadSnapshotQueryDAO
1. UITemplateManagementDAO
## Register all service implementations
In `public void prepare()`, use `this#registerServiceImplementation` method to register and bind with your implementation of the above interfaces.

## Example
`org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchProvider` and `org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLStorageProvider`  are good examples.

## Redistribution with new storage implementation
To implement the storage, you don't have to clone the main repo. Simply use our Apache releases.
Take a look at [SkyAPM/SkyWalking-With-Es5x-Storage](https://github.com/SkyAPM/SkyWalking-With-Es5x-Storage) repo, SkyWalking v6 redistribution with ElasticSearch 5 TCP connection storage implementation.
