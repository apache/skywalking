Apache IoTDB - Apache Skywalking适配器
==========

## 项目信息

项目名称：Apache IoTDB - Apache Skywalking适配器

项目描述：Apache IoTDB是高性能时序数据库，Skywalking是流行的APM（Application Performance Monitor）系统，用于监测各微服务或接口调用之间的延迟。目前，Skywalking支持使用InfluxDB、Elasticsearch等系统存储采集到的延迟指标数据。我们希望能够为Skywalking提供一个使用IoTDB进行相关数据读写的适配器。

[项目链接](https://summer.iscas.ac.cn/#/org/prodetail/210070771)

## 实现思路

项目目标是为Skywalking开发一个使用IoTDB进行相关数据读写的适配器。可以参考Skywalking目前已有的采用InfluxDB、H2和Elasticsearch的适配器。Skywalking给出了存储拓展的开发指南，参考[链接](https://github.com/apache/skywalking/blob/master/docs/en/guides/storage-extention.md) 。此外，需要使用Skywalking和InfluxDB的调试过程，以便充分了解Skywalking的接口和使用方式。

IoTDB建议使用其提供的原生客户端封装: Session或Session Pool与IoTDB服务端进行交互。

开发平台：Win10，IoTDB版本：0.12.2

## 相关概念

### Skywalking的存储模型

Skywalking 8.0+的存储模型大致分为4类：Record，Metrics，NoneStream，ManagementData。它们都实现了StorageData接口。storageData接口必须实现id()方法，下面分别介绍4类存储模型：

- Record：Record大部分是原始日志数据或任务记录。这些数据需要持久化，无需进一步分析。所有Record类的模型均具备time_bucket字段，用于记录当前Record所在的时间窗口。具体例子有：SegmentRecord，AlarmRecord，BrowserErrorLogs，LogRecord，ProfileTaskLogRecord， ProfileThreadSnapshotRecord，TopN。
    - SegmentRecord：Trace Segment明细记录模型。由skywalking-trace-receiver-plugin插件接收并解析Skywalking Agent发送来的链路数据后得到TraceSegment。
    - AlarmRecord：报警明细记录模型。在指标触发报警规则时，会产生对应的报警明细数据模型。
    - TopN：TopN是采样模型，具备statement字段（用于描述采样数据的关键信息），latency字段（用于记录采样数据的延迟），
      trace_id字段（用于描述采样数据的关联分布式链路ID），service_id字段（用于记录服务ID）。目前采样模型默认只有TopNDatabaseStatement。
        - TopNDatabaseStatement：按照延迟排序的DB采样记录。
- Metrics：Metrics表示统计数据，是通过OAL脚本或硬编码对源（Source）数据进行聚合分析后生成的存储模型。它的生命周期由TTL（生存时间）控制。所有Metrics类的模型均具备time_bucket和entity_id字段。例如：NetworkAddressAlias，Event，InstanceTraffic，EndpointTraffic， ServiceTraffic，EndpointRelationServerSideMetrics，ServiceInstanceRelationServerSideMetrics， ServiceInstanceRelationClientSideMetrics，ServiceRelationServerSideMetrics，ServiceRelationClientSideMetrics

- NoneStream：NoneStream基于Record，支持time_bucket转换为TTL。例如：ProfileTaskRecord

- ManagementData：UI模板管理相关的数据，默认只有一个UITemplate实现类

### IoTDB的数据模型

参考IoTDB官方[数据模型介绍](https://iotdb.apache.org/zh/UserGuide/V0.12.x/Data-Concept/Data-Model-and-Terminology.html)
。简单来说，可以用树结构来认识IoTDB的数据模型。如果按照层级划分，从高到低依次为：Storage Group -> (LayerName) -> Device -> Timeseries。从最上层到其下某一层称为一条路径（Path），最上层是Storage Group，倒数第二层是Device，倒数第一层是Timeseries，中间可以有很多层，每一层姑且称之为LayerName。

值得注意的是，每个Storage Group需要一个线程，所以Storage Group过多会导致存储性能下降。此外，LayerName的值存储在内存中，而Timeseries的值及其下的数据存储在硬盘中。

## Skywalking的IoTDB-adapter存储方案

### 概念划分

Skywalking的每个存储模型可以认为是一个Model，Model中包含了多个Column，每个Column中具备ColumnName和ColumnType属性，分别表示Column的名字和类型，每个ColumnName下存储多个数据类型为ColumnType的数据Value。从关系型数据库的角度来看的话，Model即是关系表，Column即是关系表中的字段。

### 方案一：类似关系型数据库的存储方案（无法实现）

将Skywalking的所有存储模型都写入IoTDB的一个存储组中，例如root.skywalking存储组。Model对应Device，Column对应Timeseries。即Skywalking的“Database -> Model -> Column”对应到IoTDB的“Storage Group -> Device -> Timeseries”。该方案的IoTDB存储路径只有4层：root.skywalking.ModelName.ColumnName。该方案的优点是逻辑清晰，实现难度较低，但由于数据都存储在硬盘上，查询效率相对较差。

> 验证结果：该方案无法实现  
> 原因：部分存储接口需要实现group by entity_id的查询功能，但IoTDB只支持group by time。需要采用方案二并通过group by level来实现。

### 方案二：引入索引的存储方案

由于IoTDB的每个LayerName存储于内存中，可以将其认为是一种索引，可以充分利用LayerName的这个特性提高IoTDB的查询性能。

依然将Skywalking的所有存储模型都写入IoTDB的一个存储组中，例如root.skywalking存储组。Model对应一个LayerName，需要索引的Column也对应于LayerName，不过LayerName并不存储ColumnName，而是存储对应的Value，相当于需要索引的一个Column的不同Value存储在同一分支下的同一层。不需要索引的Column依然对应Timeseries，即路径的最后一层。最终将导致同一个Model的数据分散到多个Device中。

由于该方案丢失了需要索引的ColumnName，所以需要通过硬编码记录需要索引的ColumnName及ColumnType。此外为了避免存储的混乱，还需要保证一个Model下多个索引Column的顺序。计划通过硬编码存储Model需要索引的Column的存储顺序。

该方案的IoTDB存储路径长度是不定的，索引的Column越多，路径的长度越长。例如:

当前有model1(<u>column11</u>, column12)，model2(<u>column21</u>, <u>column22</u>, column23)，model3(column31)，下划线说明该字段需要索引。
- 需要具备索引的Model：  
  - `root.skywalking.model1_name.column11_value.column12_name`
  - `root.skywalking.model2_name.column21_value.column22_value.column23_name`
- 不需要具备索引的Model:   
  - `root.skywalking.model3_name.column31_Name`

插入：
```sql
-- 插入model1
insert into root.skywalking.model1_name.column11_value
values (timestamp, column12_value);
-- 插入model2
insert into root.skywalking.model2_name.column21_value.column22_value
values (timestamp, column23_name);
```

查询：
```sql
-- 查找model1的所有数据
select *
from root.skywalking.model1_name;
-- 查找model2中column22_value="test"的数据
select *
from root.skywalking.model2_name.*.test;
-- 按照column21分组统计model2中column23的和
select sum(column23)
from root.skywalking.model2_name.*.*
group by level = 3;
```

该方案的优点是实现了索引功能，类似InfluxDB的tag，但逻辑较复杂，实现难度较大。另一方面还需进一步确定哪些Column需要作为索引列，这一点可以参考Elasticsearch（StorageEsInstaller），InfluxDB（TableMetaInfo），MySQL（MySQLTableInstaller）的实现，以及ModelColumn的isStorageOnly属性。

该方案将部分数据通过LayerName存储在内存中，在海量数据的情况下可能会导致内存开销较大。

目前可以确定的索引字段
> id  
> entity_id  
> node_type  
> service_group  
> service_id  
> trace_id

此外，可以将time_bucket转换为IoTDB自带的时间戳timestamp后进行存储，而无需另行存储time_bucket

### 方案的性能测试

参考[设计文档](https://github.com/jun0315/iotdb-influxdb) 。该设计文档来自[开源软件供应链点亮计划 - 暑期2021的**兼容InfluxDB协议或客户端**](https://summer.iscas.ac.cn/#/org/prodetail/210070151) 项目，可以看到，使用索引和不使用索引的查询时间有数倍的差距。

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

- 中期成果提交之前  
采用**方案一：类似关系型数据库的存储方案**实现IoTDB适配器，实现过程中参考了JDBC和InfluxDB存储插件。使用SessionPool与IoTDB服务端进行交互。插入和更新操作使用insertRecords， 查询操作使用executeQueryStatement，删除操作使用deleteData。已实现插入、更新、删除的相关接口，查询接口仅部分实现（<u>由于IoTDB只支持group by time和group by level，后续将采用方案二完全实现所有接口</u>）。

- 中期成果提交之后  
采用**方案二：引入索引的存储方案**对项目进行重构，同时增加单元测试用例。目前已完成数据的插入、过滤查询、模糊查询等接口。另一方面，积极参与社区，向Skywalking社区和IoTDB社区提出Issue和Discussion，向Skywalking社区提交修复Bug的PR。当采用方案二时，由于同一个model的数据被分散到了多个device中，所以排序查询和聚合查询都需要跨device查询。但IoTDB目前对这方面的支持并不完善，无法满足需求，往往**需要使用暴力法全部查出来以后自行实现排序和聚合**。

### 对社区的参与和贡献
#### Skywalking
- Issue:
  - [A difference of IBrowserLogQueryDAO in storage plugins #7527](https://github.com/apache/skywalking/issues/7527)
  - [[Bug] H2MetadataQueryDAO.searchService doesn't support group #7653](https://github.com/apache/skywalking/issues/7653)
  - [[Bug] ProfileThreadSnapshotQuery.queryProfiledSegments adopts a wrong sort function #7695](https://github.com/apache/skywalking/issues/7695)
  - [[Bug] The methods of H2EventQueryDAO doesn't sort the data by Event.START_TIME #7713](https://github.com/apache/skywalking/issues/7713)
  - [[Bug] H2LogQueryDAO and LogQuery don't support fuzzy query of AbstractLogRecord.CONTENT #7723](https://github.com/apache/skywalking/issues/7723)
- Pull Request:
  - [Fix issue#7527, add desc sort function in IBrowserLogQueryDAO of H2 and ES #7580](https://github.com/apache/skywalking/pull/7580)
  - [Fix H2MetadataQueryDAO.searchService doesn't support auto grouping. #7654](https://github.com/apache/skywalking/pull/7654)
  - [Fix "ProfileThreadSnapshotQuery.queryProfiledSegments" adopts a wrong sort function #7696](https://github.com/apache/skywalking/pull/7696)
  - [Fix H2EventQueryDAO doesn't sort data by Event.START_TIME and uses a wrong pagination query #7720](https://github.com/apache/skywalking/pull/7720)

#### IoTDB
- Issue:
  - [Hope select function, "top_k" and "bottom_k", could support "group by level" #3905](https://github.com/apache/iotdb/issues/3905)
  - [Hope fuzzy query could support multi-device #3945](https://github.com/apache/iotdb/issues/3945)
  - ["group by level" cannot get the node name #4006](https://github.com/apache/iotdb/issues/4006)
- Discussion:
  - [一个有关排序查询的问题（A problem about sort query）#3888](https://github.com/apache/iotdb/discussions/3888)
  - [一个有关聚合查询的问题（A problem about aggregation query）#3907](https://github.com/apache/iotdb/discussions/3907)

### Skywalking-IoTDB适配器设置参数：

1. host，IoTDB主机IP，默认127.0.0.1
2. rpcPort，IoTDB监听端口，默认6667
3. username，用户名，默认root
4. password，密码，默认root
5. storageGroup，存储组名称，默认root.skywalking
6. sessionPoolSize，SessionPool大小，默认16（后续可配置为主机核心线程数）
7. fetchTaskLogMaxSize，在一次请求中获取的TaskLog数量的最大值，默认1000

## 遇到的问题及解决方案

> 问题1：Skywalking部分存储接口要求`order by`查询，但IoTDB仅支持`order by time`。这本来可以使用选择函数`top_k`和`bottom_k`来过滤数据。但在使用索引方案的情况下，由于数据点分散在多个device中，`top_k`和`bottom_k`函数无法过滤数据，也无法使用类似`group by level`的合并device的查询方法，具体的描述可参考：[Discussion #3888](https://github.com/apache/iotdb/discussions/3888), [Issue #3905](https://github.com/apache/iotdb/issues/3905)
>>解决方案：目前除暴力法全部查询出来再排序以外暂无其他解决方案。

>问题2：Skywalking部分存储接口要求`group by entity_id`的分组聚合查询，但IoTDB仅支持`group by time`和`group by level`
>>解决方案：采用方案二，并将entity_id作为LayerName存储，通过group by level实现分组查询。

>问题3：在使用索引方案的情况下，由于数据点分散在多个device中，聚合函数的使用必须要通过`group by level`才能正确统计数据。但在此情况下，`group by level`和`where`同时使用得不到预期的结果。应该要加上`align by device`语义才行，但加上以后就会引起IllegalPathException，推测是因为IoTDB不能同时支持`group by level`和`align by device`的语义。具体描述可以参考[Discussion #3907](https://github.com/apache/iotdb/discussions/3907)
>>解决方案：运用`align by device`和`where`过滤查询所有数据后自行实现聚合函数的功能。

>问题4：Skywalking的存储接口要求模糊查询，但IoTDB使用like的模糊查询并不支持跨device查询，不适用于方案二。此外，IoTDB的字符串函数string_contains只能返回true/false，并不会对数据进行过滤。具体描述可以参考：[Issue #3945](https://github.com/apache/iotdb/issues/3945)
>>解决方案：最初使用`select *, string_contains`获得查询结果后再根据true/false循环过滤。  
>>后来[@ijihang](https://github.com/ijihang) 提交的[PR#3953](https://github.com/apache/iotdb/pull/3953) ，使like支持跨device查询和`align by device`。所以该问题可以直接使用类似MySQL的模糊查询即可，再次感谢。

>问题5：Skywalking的存储接口要求模糊查询和过滤查询。由于采用了索引方案，所以需要跨device查询，但使用`string_contains`函数的过滤查询对多个device之间采用了and的语义，无法正确过滤数据，如果是or的语义应该就可以正确过滤数据了。同时`string_contains`也不支持使用`align by device`。以上这种情况仅支持对time的过滤。
>>解决方案：最初使用`select *, string_contains from root.skywalking.xxx.* where time > ?`获得结果后自行对其他条件过滤。  
>>后来采用类似问题4的解决方案，直接使用类似MySQL的模糊查询和过滤查询即可。

## 后续工作安排

- 熟悉Skywalking的基本使用操作，阅读Elasticsearch存储插件的源码
- 采用方案二重构项目，实现所有接口
- 进行E2E测试
