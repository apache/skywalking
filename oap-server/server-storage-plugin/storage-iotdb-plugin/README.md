Apache IoTDB - Apache Skywalking适配器
==========

## 项目信息

项目名称：Apache IoTDB - Apache Skywalking适配器

项目描述：Apache IoTDB是高性能时序数据库，Skywalking是流行的APM（Application Performance Monitor）系统，用于监测各微服务或接口调用之间
的延迟。目前，Skywalking支持使用InfluxDB、Elasticsearch等系统存储采集到的延迟指标数据。我们希望能够为Skywalking提供一个使用IoTDB进行相关数据读写的适配器。

## 实现思路

项目目标是为Skywalking开发一个使用IoTDB进行相关数据读写的适配器。可以参考Skywalking目前已有的采用InfluxDB、H2和Elasticsearch的适配器。Skywalking给出了存储拓展的
开发指南，参考[链接](https://github.com/apache/skywalking/blob/master/docs/en/guides/storage-extention.md) 。此外，需要使用
Skywalking和InfluxDB的调试过程，以便充分了解Skywalking的接口和使用方式。

IoTDB建议使用其提供的原生客户端封装: Session或Session Pool与IoTDB服务端进行交互。

## 相关概念

### Skywalking的存储模型

Skywalking 8.0+的存储模型大致分为4类：Record，Metrics，NoneStream，ManagementData。它们都实现了StorageData接口。storageData接口必须实现id()
方法，下面分别介绍4类存储模型：

- Record：Record大部分是原始日志数据或任务记录。这些数据需要持久化，无需进一步分析。所有Record类的模型均具备time_bucket字段，用于记录
  当前Record所在的时间窗口。具体例子有：SegmentRecord，AlarmRecord，BrowserErrorLogs，LogRecord，ProfileTaskLogRecord，
  ProfileThreadSnapshotRecord，TopN。
    - SegmentRecord：Trace Segment明细记录模型。由skywalking-trace-receiver-plugin插件接收并解析Skywalking Agent发送来的链路数据后得到Trace
      Segment。
    - AlarmRecord：报警明细记录模型。在指标触发报警规则时，会产生对应的报警明细数据模型。
    - TopN：TopN是采样模型，具备statement字段（用于描述采样数据的关键信息），latency字段（用于记录采样数据的延迟），
      trace_id字段（用于描述采样数据的关联分布式链路ID），service_id字段（用于记录服务ID）。目前采样模型默认只有TopNDatabaseStatement。
        - TopNDatabaseStatement：按照延迟排序的DB采样记录。
- Metrics：Metrics表示统计数据，是通过OAL脚本或硬编码对源（Source）数据进行聚合分析后生成的存储模型。它的生命周期由TTL（生存时间）控制。
  所有Metrics类的模型均具备time_bucket和entity_id字段。例如：NetworkAddressAlias，Event，InstanceTraffic，EndpointTraffic，
  ServiceTraffic，EndpointRelationServerSideMetrics，ServiceInstanceRelationServerSideMetrics，
  ServiceInstanceRelationClientSideMetrics，ServiceRelationServerSideMetrics，ServiceRelationClientSideMetrics

- NoneStream：NoneStream基于Record，支持time_bucket转换为TTL。例如：ProfileTaskRecord

- ManagementData：UI模板管理相关的数据，默认只有一个UITemplate实现类

### IoTDB的数据模型

参考IoTDB官方[数据模型介绍](https://iotdb.apache.org/zh/UserGuide/V0.12.x/Data-Concept/Data-Model-and-Terminology.html)
。简单来说，可以用树结构来认识IoTDB的数据模型。如果按照层级划分，从高到低依次为：Storage Group -> (LayerName) -> Device ->
Timeseries。从最上层到其下某一层称为一条路径（Path），最上层是Storage Group，倒数第二层是Device，倒数第一层是Timeseries，中间可以有很多层，每一层姑且称之为LayerName。

值得注意的是，每个Storage Group需要一个线程，所以Storage Group过多会导致存储性能下降。此外，LayerName的值存储在内存中，而Timeseries的值及其下的数据存储在硬盘中。

## Skywalking的IoTDB-adapter存储方案

### 概念划分

Skywalking的每个存储模型可以认为是一个Model，Model中包含了多个Column，每个Column中具备ColumnName和ColumnType属性，分别表示Column的
名字和类型，每个ColumnName下存储多个数据类型为ColumnType的数据Value。从关系型数据库的角度来看的话，Model即是关系表，Column即是关系表中的字段。

### 方案一：类似关系型数据库的存储方案

将Skywalking的所有存储模型都写入IoTDB的一个存储组中，例如root.skywalking存储组。Model对应Device，Column对应Timeseries。即Skywalking的“Database -> Model
-> Column”对应到IoTDB的“Storage Group -> Device -> Timeseries”。该方案的IoTDB存储路径只有4层：root.skywalking.ModelName.ColumnName。
该方案的优点是逻辑清晰，实现难度较低，但由于数据都存储在硬盘上，查询效率相对较差。

### 方案二：引入索引的存储方案

由于IoTDB的每个LayerName存储于内存中，可以将其认为是一种索引，可以充分利用LayerName的这个特性提高IoTDB的查询性能。

依然将Skywalking的所有存储模型都写入IoTDB的一个存储组中，例如root.skywalking存储组。Model对应一个LayerName，需要索引的Column也对应于LayerName，
不过LayerName并不存储ColumnName，而是存储对应的Value，相当于需要索引的一个Column的不同Value存储在同一分支下的同一层。不需要索引的Column依然对应Timeseries，即路径的最后一层。

由于该方案丢失了需要索引的ColumnName，所以需要通过硬编码记录需要索引的ColumnName及ColumnType。此外为了避免存储的混乱，还需要记录一个Model下多个索引Column的顺序。

该方案的IoTDB存储路径长度是不定的，索引的Column越多，路径的长度越长。例如:

- 需要具备索引的Model：root.skywalking.model1_name.column11_value.column12_name，
  root.skywalking.model2_name.column21_value.column22_value.column23_name
- 不需要具备索引的Model: root.skywalking.model3_name.column31_Name

该方案的优点是实现了索引功能，类似InfluxDB的tag，但逻辑较复杂，实现难度较大。此外还需进一步确定哪些Column需要作为索引列，这一点可以参考Elasticsearch（StorageEsInstaller），InfluxDB（TableMetaInfo），MySQL（MySQLTableInstaller）的实现。

### 方案的性能测试

参考[设计文档](https://github.com/jun0315/iotdb-influxdb) 。该设计文档来自**开源软件供应链点亮计划 - 暑期2021**的<u>兼容InfluxDB协议或客户端</u>
项目，可以看到，使用索引和不使用索引的查询时间有数倍的差距。

## 时间规划

|时间安排|工作内容|
|----|----|
|07月01日-07月11日|熟悉开源项目工作流程，完成本地开发环境配置，了解IoTDB和Skywalking的操作方法|
|07月12日-07月25日|了解IoTDB和Skywalking项目架构，熟悉项目相关模块与接口的详细设计，进行本地Demo的调试|
|07月26日-08月08日|设计并实现IoTDB的Skywalking适配器|
|08月09日-08月15日|完成单元测试，准备中期研发成果的提交|
|08月16日-08月29日|在中期成果的基础上优化设计，并针对项目设计并实施性能评估和测试|
|08月30日-09月12日|在测试结果的基础上不断优化设计，整理项目成果后向社区提交|
|09月13日-09月19日|根据社区审阅意见进行修改|
|09月20日-09月30日|社区审阅通过后合并代码，准备结项研发成果的提交|

## 已完成工作

目前采取**方案一：类似关系型数据库的存储方案**实现IoTDB适配器。使用SessionPool与IoTDB服务端进行交互。插入和更新操作使用insertRecords，
查询操作使用executeQueryStatement，删除操作使用deleteData。目前已实现插入、更新、删除的相关接口，查询接口仅部分实现。等完全实现后一块提交代码。

### 对用户开放如下配置：

1. host，IoTDB主机IP，默认127.0.0.1
2. rpcPort，IoTDB监听端口，默认6667
3. username，用户名，默认root
4. password，密码，默认root
5. sessionPoolSize，SessionPool大小，默认3（后续可配置为主机核心线程数）
6. rpcCompression，是否启用rpc压缩，默认false
7. storageGroup，存储组名称，默认root.skywalking

## 遇到的问题及解决方案

- 问题1：IoTDB的选择函数TOP_K和BOTTOM_K在和其它字段一起查询时无法进行过滤
    - 解决方案：通过两次查询实现需求
- 问题2：参考InfluxDB和H2存储实现的过程中对其中部分操作理解不到位
    - 解决方案：在Skywalking社区提问
- 问题3：对IoTDB的特性和操作理解不够
    - 解决方案：阅读官方文档，与导师进行沟通

## 后续工作安排

- 继续实现方案一，熟悉其它存储插件对各个接口的实现，增加与社区的沟通
- 对方案一进行完整测试
- 采用方案二重构项目，比较两种方案的性能
