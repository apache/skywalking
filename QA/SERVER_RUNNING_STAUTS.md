### 通过服务器日志的Server Health Collector Report分析运行情况
- Health Report会在服务器定时出现，报告在两次报告时间段内的运行情况

1. ServerReceiver反映服务端接收数据的情况
1. DataBufferThread反映接收到的数据，异步写入log文件的情况
1. PersistenceThread反映log文件中的内容读取情况
1. PersistenceThread extra:hbase反映log文件中的内容，持久化到hbase的情况
1. RedisInspectorThread反映和redis的连接池检测情况

- Health Report示例如下
```
---------Server Health Collector Report---------
id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:DataBufferThread_0(27)>
[INFO]DataBuffer flush data to local file:1460445849010-a4ea1e20927b40d290ebd4f5f3e08705(t:1460598787106)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:DataBufferThread_1(28)>
[INFO]DataBuffer flush data to local file:1460445849012-0ec5a44474bc4969a9194fa1b1cadf73(t:1460598848608)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:DataBufferThread_2(29)>
[INFO]DataBuffer flush data to local file:1460445849012-4182e3d0205e48968b59fe9fbb0923b4(t:1460598668464)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:DataBufferThread_4(31)>
[INFO]DataBuffer flush data to local file:1460445849013-54216553363343fe8b39221a0691cf40(t:1460598788488)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:DataBufferThread_5(32)>
[INFO]DataBuffer flush data to local file:1460445849013-531a955690764a56ac0490db531bec10(t:1460598727104)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:DataBufferThread_6(33)>
[INFO]DataBuffer flush data to local file:1460445849014-e0163d8c8b8446d6812a11e89cd48b0e(t:1460598787722)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:DataBufferThread_7(34)>
[INFO]DataBuffer flush data to local file:1460445849014-340435a1dd9e49f088af039c2e9e85af(t:1460598787510)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:DataBufferThread_8(35)>
[INFO]DataBuffer flush data to local file:1460445849015-5dacc5f6fb38442eb484b50a240568df(t:1460598727508)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_0(15)>
[INFO]read 224 chars from local file:1460445849014-340435a1dd9e49f088af039c2e9e85af(t:1460598787534)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_0(15),extra:hbase>
[INFO]save 1 BuriedPointEntries.(t:1460598787549)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_1(16)>
[INFO]read 268 chars from local file:1460445849013-531a955690764a56ac0490db531bec10(t:1460598727121)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_1(16),extra:hbase>
[INFO]save 1 BuriedPointEntries.(t:1460598727129)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_4(19)>
[INFO]read 238 chars from local file:1460445849013-54216553363343fe8b39221a0691cf40(t:1460598788522)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_4(19),extra:hbase>
[INFO]save 1 BuriedPointEntries.(t:1460598788532)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_5(20)>
[INFO]read 526 chars from local file:1460445849014-e0163d8c8b8446d6812a11e89cd48b0e(t:1460598787770)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_5(20),extra:hbase>
[INFO]save 1 BuriedPointEntries.(t:1460598787775)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_6(21)>
[INFO]read 238 chars from local file:1460445849010-a4ea1e20927b40d290ebd4f5f3e08705(t:1460598787126)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_6(21),extra:hbase>
[INFO]save 1 BuriedPointEntries.(t:1460598787135)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_7(22)>
[INFO]read 583 chars from local file:1460445849012-4182e3d0205e48968b59fe9fbb0923b4(t:1460598668466)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_7(22),extra:hbase>
[INFO]save 1 BuriedPointEntries.(t:1460598668473)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_8(23)>
[INFO]read 819 chars from local file:1460445849015-5dacc5f6fb38442eb484b50a240568df(t:1460598727531)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_8(23),extra:hbase>
[INFO]save 2 BuriedPointEntries.(t:1460598727542)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_9(24)>
[INFO]read 462 chars from local file:1460445849012-0ec5a44474bc4969a9194fa1b1cadf73(t:1460598848648)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:PersistenceThread_9(24),extra:hbase>
[INFO]save 3 BuriedPointEntries.(t:1460598848757)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:RedisInspectorThread(65)>
[INFO]alarm redis connectted.(t:1460598845054)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:RegisterPersistenceThread(14)>
[INFO]flush memory register to file.(t:1460598848748)

id<SkyWalkingServer,M:host-10-1-241-17/61.50.248.117,P:18737,T:ServerReceiver(39)>
[INFO]DataBuffer reveiving data.(t:1460598845300)

------------------------------------------------
```