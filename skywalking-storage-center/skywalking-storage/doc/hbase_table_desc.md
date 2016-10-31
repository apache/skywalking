# Sky Walking Server HBase存储结构说明
## sw-call-chain
- 此表用于存储追踪到明细的日志信息，包含左右从客户端收集到的日志全文。
- Analysis程序会根据此明细进行调用情况的分析和汇总。

### 存储结构
* row_key: traceid。如：1.0a2.1455720324167.4c6b535.22455.2629.289
* column_family: (常量)call-chain
* column_name:动态列名，列名称由各埋点的序号决定。其中RPC调用为两列，服务端列名为序号后加“-S”
* column_value:埋点序列化后的字符串数据

### 存储结构示例
对于row_id = 1.0a2.1455720324167.4c6b535.22455.2629.289存储结构如下：

|column_family|column_name|column_value|
| ----------- |---------| ----------|
|call-chain|...|...|
|call-chain|0.0.0-S|1.0a2.1455720324167.4c6b535.22455.2629.289@~0.0@~0@~dubbo://10.1.31.12:20188/com.ai.aisse.core.rest.ExpenseInitApi.searchMembersinfo(String)@~1455720324205@~38@~ITSC-MIS-LEV-web01/10.1.31.12@~0@~ @~D@~true@~ @~17112@~aisse-dubbo@~5@~S|
|call-chain|0.0.0.0|1.0a2.1455720324167.4c6b535.22455.2629.289@~0.0.0@~0@~com.ai.aisse.core.dao.impl.EmployeeInfoDaoImpl.selectEmployee(java.lang.String)@~1455720324209@~19@~ITSC-MIS-LEV-web01/10.1.31.12@~0@~ @~M@~false@~@~17112@~aisse-dubbo@~5@~L|
|call-chain|0.0.0.0.0|...|
|call-chain|0.0.0.1|...|
|call-chain|...|...|

