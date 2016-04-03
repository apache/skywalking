### 部署第三方软件
- JDK 1.7
- 安装zookeeper 3.4.6
- 安装apache hbase 1.1.2
- 安装mysql
- 安装tomcat 7
- redis-3.0.5

### 编译安装SkyWalking Server
- 编译工程
```shell
$cd github/sky-walking/skywalking-server
$mvn package -Dmaven.test.skip=true
$cd github/sky-walking/skywalking-server/target/installer
```
- 拷贝installer到服务器
- 根据服务器环境修改/config/config.properties
```properties
#服务器收集数据监听端口
server.port=34000

#数据缓存文件目录，请确保此目录有一定的存储容量
buffer.data_buffer_file_parent_directory=D:/test-data/data/buffer
#偏移量注册文件的目录，这里为系统绝对路径
registerpersistence.register_file_parent_directory=d:/test-data/data/offset

#hbase zk quorum，hbase的zk地址
hbaseconfig.zk_hostname=10.1.235.197,10.1.235.198,10.1.235.199
#hbase zk port，hbase的zk使用端口
hbaseconfig.client_port=29181

#告警数据暂存的Redis配置
alarm.redis_server=10.1.241.18:16379
```
- 启动服务
```shell
$cd installer/bin
$./swserver.sh
```
- 可根据需要部署多个实例
- 启动服务前，请注意hbase的客户端使用机器名而非ip连接主机，请在server所在机器上正确配置hosts文件，否则会造成数据无法入库

### 编译安装SkyWalking Alarm
- 编译工程
```shell
$cd github/sky-walking/skywalking-alarm
$mvn package -Dmaven.test.skip=true
$cd github/sky-walking/skywalking-alarm/target/installer
```
- 拷贝installer到服务器
- 根据服务器环境修改/config/config.properties
```properties
#zookeeper连接地址,用于协调集群，可以和hbase的zookeeper共用
zkpath.connect_str=10.1.241.18:29181,10.1.241.19:29181,10.1.241.20:29181

#管理数据库的JDBC连接信息
#数据库连接地址
db.url=jdbc:mysql://10.1.241.20:31306/sw_db
#数据库用户名
db.user_name=sw_dbusr01
#数据库密码
db.password=sw_dbusr01

#告警信息存在的redis服务器地址，需要和skywalking-server的alarm.redis_server设置一致
alarm.redis_server=127.0.0.1:6379
```
- 启动服务
```shell
$cd installer/bin
$./sw-alarm-server.sh
```
- 可根据需要部署多个实例，根据实例启动数量，自动负载均衡

### 编译安装SkyWalking WebUI
- 修改配置文件config.properties
```properties
#hbase的连接地址
hbaseconfig.quorum=10.1.235.197,10.1.235.198,10.1.235.199
hbaseconfig.client_port=29181
```
- 修改配置文件jdbc.properties
```properties
#管理数据库的JDBC连接信息
jdbc.url=jdbc:mysql://10.1.228.202:31316/test
jdbc.username=devrdbusr21
jdbc.password=devrdbusr21
```
- 编译工程
```shell
$cd github/sky-walking/skywalking-webui
$mvn package
```
- 初始化管理数据库
根据[数据库脚本](https://github.com/wu-sheng/sky-walking/blob/master/skywalking-webui/src/main/sql/table.mysql)初始化管理数据库。其中，脚本中如下SQL片段需要修改：
```sql
--配置告警邮件的发送人和SMTP信息
INSERT INTO `system_config` (`config_id`,`conf_key`,`conf_value`,`val_type`,`val_desc`,`create_time`,`sts`,`modify_time`) VALUES (1000,'mail_info','{\"mail.host\":\"mail.asiainfo.com\",\"mail.transport.protocol\":\"smtp\",\"mail.smtp.auth\":\"true\",\"mail.smtp.starttls.enable\":\"false\",\"mail.username\":\"testA\",\"mail.password\":\"******\",\"mail.account.prefix\":\"@asiainfo.com\"}','json','默认邮件发送人信息','2015-12-10 11:54:06','A','2015-12-10 11:54:06');
--配置部署页面地址，用于告警邮件内的链接
INSERT INTO `system_config` (`config_id`,`conf_key`,`conf_value`,`val_type`,`val_desc`,`create_time`,`sts`,`modify_time`) VALUES (1001,'portal_addr','http://10.1.235.197:48080/skywalking/','string','默认门户地址','2015-12-10 15:23:53','A','2015-12-10 15:23:53');
--配置SkyWalking Server的集群地址（内网地址）
INSERT INTO `system_config` (`config_id`,`conf_key`,`conf_value`,`val_type`,`val_desc`,`create_time`,`sts`,`modify_time`) VALUES (1002,'servers_addr','10.1.235.197:34000;10.1.235.197:35000;','string','日志采集地址','2015-12-10 15:23:53','A','2015-12-10 15:23:53');
--配置SkyWalking Server的集群地址（外网地址）
INSERT INTO `system_config` (`config_id`,`conf_key`,`conf_value`,`val_type`,`val_desc`,`create_time`,`sts`,`modify_time`) VALUES (1003,'servers_addr_1','60.194.3.183:34000;60.194.3.183:35000;60.194.3.184:34000;60.194.3.184:35000;','string','日志采集地址-外网','2015-12-10 15:23:53','A','2015-12-10 15:23:53');
```
- 上传war包到服务器，启动Tomcat服务器

### 编译安装SkyWalking Analysis
暂未提供

## 使用maven发布各插件工程
- 发布skywalking-sdk-plugin下的各子工程(dubbo-plugin，spring-plugin，web-plugin，jdbc-plugin，httpclient-4.2.x-plugin，httpclient-4.3.x-plugin)
- 请跳过maven.test环节，避免打包失败
```properties
-Dmaven.test.skip=true
```