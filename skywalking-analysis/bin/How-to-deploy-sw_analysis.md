# 怎么部署分析模块 How to deploy skywalking-analysis

## 前置步骤 Prepared deploy skywalking-analysis
1. 将HBase安装包拷贝到Hadoop安装目录下. Copy HBase installation package to the Hadoop installation directory.
2. 用HBase的主节点的配置覆盖HBase的安装包里面的配置. Use the configuration of the HBase master node converting the new Hbase package
3. 在.bash_profile文件添加下面的配置,(需要根据实际情况进行配置). Add the following configuration to .base_profile
```
export HBASE_HOME=/aifs01/users/hdpusr01/hbase-1.1.2
export PATH=$HBASE_HOME/bin:$PATH
```
4. 运行以下命令. Run the command as follow.
```
source .bash_profile
echo ${HBASE_HOME}
```


## 部署模块 Deploy package
1. git clone analysis工程. Clone the analysis code from github
2. 根据实际情况修改src/main/resources目录下的analysis.conf. Modify the analysis.conf file that inside src/main/resources directory.
```
hbase.zk_quorum=10.1.235.197,10.1.235.198,10.1.235.199
hbase.zk_client_port=29181

mysql.url=jdbc:mysql://10.1.228.202:31316/test
mysql.username=devrdbusr21
mysql.password=devrdbusr21
```
3. 运行skywalking-webui包下的创建Table的命令. Run the create table sql script.
```
# 新增sw_chain_detail，用于调用链分析表
CREATE TABLE `sw_chain_detail` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `viewpoint` varchar(8192) COLLATE utf8_bin DEFAULT NULL,
  `treeId` varchar(40) COLLATE utf8_bin DEFAULT NULL,
  `uid` varchar(32) COLLATE utf8_bin DEFAULT NULL,
  `traceLevelId` varchar(32) COLLATE utf8_bin DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id_UNIQUE` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8146 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
SELECT * FROM sw_chain_detail;
```
4. 打包. Package the project

```
package -Dmaven.test.skip=true
```
5. 上传skywalking-analysis-1.0-SNAPSHOT.jar. Upload the skywalking-analysis-1.0-SNAPSHOT.jar
6. 上传start-analysis.sh. Upload the start-analysis.sh
7. 修改权限. Change mode start-analysis.sh
```
> chmod +x start-analysis.sh
```
8. 运行脚本. Run the command.
```
>./start-analysis.sh
```
9. 查看日志. tail the log
```
skywalking-analysis/log> tail -1f map-reduce.log
```

