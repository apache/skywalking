# Sky Walking
SkyWalking: Large-Scale Distributed Systems Tracing Infrastructure, 是一个对JAVA分布式应用程序集群的业务运行情况进行追踪、告警和分析的系统。
* 核心理论为[Google Dapper论文：Dapper, a Large-Scale Distributed Systems Tracing Infrastructure](http://research.google.com/pubs/pub36356.html),英语有困难的同学可参考[国内翻译](http://duanple.blog.163.com/blog/static/70971767201329113141336/)
* 本分析系统能通过不修改或少量修改代码的模式，对现有的JAVA应用或J2EE应用进行监控和数据收集，并针对应用进场进行准实时告警。此外提供大量的调用性能分析功能，解决目前的监控系统主要监控进程、端口而非应用实际性能的问题。
* 支持国内常用的dubbo以及dubbox等常见RPC框架，支持应用异常的邮件告警
* 通过[byte-buddy](https://github.com/raphw/byte-buddy)避免代码侵入性，插件将通过配置文件或动态字节码机制，完成监控。

* 通过配置文件支持的监控插件
* web-plugin
* dubbo-plugin(support dubbo/dubbox)
* spring-plugin
* jdbc-plugin
* 通过动态字节码机制支持的监控插件
* mysql-plugin（和jdbc-plugin实现模式不同，对于mysql等效）
* httpClient-4.x-plugin，同时支持4.2和4.3。原有的httpclient-4.2.x-plugin，httpclient-4.3.x-plugin由比较严重的代码侵入，将在适当以后予以移除。

# 新版本能力规划
* 提供一定的日志数据分析和展现能力，减少或者避免使用团队的二次开发

# 主要贡献者
* 吴晟 &nbsp;&nbsp;[亚信](http://www.asiainfo.com/) wusheng@asiainfo.com
* 张鑫 &nbsp;&nbsp;[亚信](http://www.asiainfo.com/) zhangxin10@asiainfo.com

# 交流
* 联系邮箱：wu.sheng@foxmail.com
* QQ群：392443393，请注明“Sky Walking交流”
* 谁在使用Sky Walking?[点击进入](https://github.com/wu-sheng/sky-walking/issues/34)。同时请各位使用者反馈下，都在哪些项目中使用。

# 整体架构图
![整体架构图](http://wu-sheng.github.io/sky-walking/sample-code/images/skywalkingClusterDeploy.jpeg)

# 典型页面展现
## 实时调用链路
* 实时链路追踪展现
![追踪连路图1](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/callChain.png)
* 实时链路追踪详细信息查看
![追踪连路图2](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/callChainDetail.png)
* 实时链路追踪日志查看
![追踪连路图3](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/callChainLog.png)
* 实时链路异常告警邮件
![告警邮件](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/alarmMail.jpg)

## 分析汇总

# Quick Start
## 编译与部署
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

## 引入核心SDK
无论试用哪种插件，都必须引入
```xml
<!-- API日志输出，客户端可指定所需的log4j2版本 -->
<!-- 2.4.1为开发过程所选用版本 -->
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>2.4.1</version>
</dependency>
<!-- 监控api，可监控插件不支持的调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-api</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## 根据所需插件，配置应用程序
- 参考[SDK用户指南](https://github.com/wu-sheng/sky-walking/tree/master/skywalking-sdk-plugin)
- 注意：插件不会引用所需的第三方组件（如Spring、dubbo、dubbox等），请自行引入所需的版本。


## 下载并设置授权文件
- 注册并登陆过skywalking-webui，创建应用。（一个用户代表一个逻辑集群，一个应用代表一个服务集群。如前后端应用应该设置两个应用，但归属一个用户）
- 下载授权文件，并在运行时环境中，将授权文件加入到CLASSPATH中

## 在运行时环境中设置环境变量
```
export SKYWALKING_RUN=true
```
- 设置完成后，可以在当前环境中启动业务应用系统

## 通过扩展log4j或log4j2，在应用日志中，显示trace-id
### log4j
- 编译并发布skywalking-log/log4j-1.x-plugin
```xml
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-log4j-1.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 配置log4j配置文件
```properties
log4j.appender.A1.layout=com.ai.cloud.skywalking.plugin.log.log4j.v1.x.TraceIdPatternLayout
#%x为traceid的转义符
log4j.appender.A1.layout.ConversionPattern=[%x] %-d{yyyy-MM-dd HH:mm:ss.SSS} %c %n[%p] %n%m%n
```

### log4j2
- 编译并发布skywalking-log/log4j-2.x-plugin
- 引用所需的日志插件
```xml
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-log4j-2.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 配置log4j2配置文件
```xml
<!--%tid为traceid的转义符-->
<PatternLayout  pattern="%d{HH:mm:ss.SSS} [%tid] [%t] %-5level %logger{36} - %msg%n"/>
```

- 日志示例
```
#tid:N/A，代表环境设置不正确或监控已经关闭
#tid: ,代表测试当前访问不在监控范围
#tid:1.0a2.1453065000002.c3f8779.27878.30.184，标识此次访问的tid信息，示例如下
[DEBUG] Returning handler method [public org.springframework.web.servlet.ModelAndView com.ai.cloud.skywalking.example.controller.OrderSaveController.save(javax.servlet.http.HttpServletRequest)] TID:1.0a2.1453192613272.2e0c63e.11144.58.1 2016-01-19 16:36:53.288 org.springframework.beans.factory.support.DefaultListableBeanFactory 
```

## 如何在追踪日志中记录日志上下文
- 使用sky walking提供的专用API，可以将日志保存到追踪日志中。示例如下：
```java
String businessKey = "phoneNumber:" + phoneNumber + ",resourceId:" + resourceId + ",mail:" + mail;
BusinessKeyAppender.setBusinessKey2Trace(businessKey);
```

## 如何在代码中获取traceid
- 通过API获取traceid
```java
Tracing.getTraceId();
```

## 还有其他方式获取traceid么？
- 通过web应用的http调用入口，通过返回的header信息，找到此次调用的traceid。前提：此web应用的url，已经使用skywalking进行监控。

# 源代码说明
* [HBase存储结构说明](https://github.com/wu-sheng/sky-walking/blob/master/skywalking-server/doc/hbase_table_desc.md)
