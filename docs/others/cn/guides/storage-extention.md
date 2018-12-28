# 数据存储扩展
SkyWalking g已经提供了多种存储解决方案。在本文档中，您可以
学习如何轻松实现新存储。

## 定义您的数据存储提供实现
1. 定义一个类扩展 `org.apache.skywalking.oap.server.library.module.ModuleProvider`.
2. 将此数据存储提供实现继承存储模块。
```java
@Override 
public Class<? extends ModuleDefine> module() {
    return StorageModule.class;
}
```

## 实现所有DAO接口
下面是所有数据存储所需实现的DAO接口

1. IServiceInventoryCacheDAO
1. IServiceInstanceInventoryCacheDAO
1. H2EndpointInventoryCacheDAO
1. H2NetworkAddressInventoryCacheDAO
1. IBatchDAO
1. StorageDAO
1. IRegisterLockDAO
1. H2TopologyQueryDAO
1. IMetricQueryDAO
1. ITraceQueryDAO
1. IMetadataQueryDAO
1. IAggregationQueryDAO
1. IAlarmQueryDAO
1. IHistoryDeleteDAO

## 注册所有的service实现
在 `public void prepare()`, 使用 `this#registerServiceImplementation` 方法去注册绑定您的接口实现。

## 实例
`org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchProvider` 
或 `org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLStorageProvider`  就是一个很好的例子。

## 重新发布新的数据存储实现
您不必为了实现数据存储而克隆主托管库，您可以轻松地依赖我们的Apache版本。看一下 [OpenSkywalking/SkyWalking-With-Es5x-Storage](https://github.com/OpenSkywalking/SkyWalking-With-Es5x-Storage) 仓库, SkyWalking v6 重新发布 ElasticSearch 5 TCP 链接数据存储的实现。