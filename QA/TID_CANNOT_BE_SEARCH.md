排查步骤：
1. 首先检查server端的buffer物理文件是否有TID
2. 如果不存在，则检查客户端的授权文件中的服务端地址是否正确或者网络是否能够ping通服务端
3. 如果存在有，则查看服务端的日志，查看持久化线程是否卡死在入HBase的过程中，一般正常的日志如下:
```
id<SkyWalkingServer,M:host-10-1-241-16/10.1.241.16,P:1982,T:PersistenceThread_2(17)>
[INFO]read 217 chars from local file:1456395555088-c8c670ceb8db4e2186a544e589db2d3e(t:1459900864671)

id<SkyWalkingServer,M:host-10-1-241-16/10.1.241.16,P:1982,T:PersistenceThread_2(17),extra:hbase>
[INFO]save 1 BuriedPointEntries.(t:1459900864676)

id<SkyWalkingServer,M:host-10-1-241-16/10.1.241.16,P:1982,T:PersistenceThread_3(18)>
[INFO]read 270 chars from local file:1456395555087-0436594d077747279cebeeece70a0ce6(t:1459900864673)

id<SkyWalkingServer,M:host-10-1-241-16/10.1.241.16,P:1982,T:PersistenceThread_3(18),extra:hbase>
[INFO]save 1 BuriedPointEntries.(t:1459900864678)

id<SkyWalkingServer,M:host-10-1-241-16/10.1.241.16,P:1982,T:PersistenceThread_4(19)>
[INFO]read 217 chars from local file:1456395555014-93b0879718ee4156926090de3037ac27(t:1459900744687)

id<SkyWalkingServer,M:host-10-1-241-16/10.1.241.16,P:1982,T:PersistenceThread_4(19),extra:hbase>
[INFO]save 1 BuriedPointEntries.(t:1459900744692)
```

持久化线程卡死HBase原因：
一般情况，如果网络通顺的话，检查部署Server端的机器中/etc/hosts是否配置HBase的域名映射
