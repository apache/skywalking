# Sky Walking
SkyWalking-Distributed Application Tracing System, 是一个对JAVA应用程序运行情况进行追踪、告警和分析的系统。
* 核心理论为[Google Dapper论文：Dapper, a Large-Scale Distributed Systems Tracing Infrastructure](http://research.google.com/pubs/pub36356.html),英语有困难的同学可参考[国内翻译](http://duanple.blog.163.com/blog/static/70971767201329113141336/)
* 本分析系统能通过不修改或少量修改代码的模式，对现有的JAVA应用或J2EE应用进行监控和数据收集，并针对应用进场进行准实时告警。此外提供大量的调用性能分析功能，解决目前的监控系统主要监控进程、端口而非应用实际性能的问题。

# 主要贡献者
* 吴晟 &nbsp;&nbsp;[亚信](http://www.asiainfo.com/) wusheng@asiainfo.com
* 张鑫 &nbsp;&nbsp;[亚信](http://www.asiainfo.com/) zhangxin10@asiainfo.com

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
## 分析汇总

# Home Page
http://wu-sheng.github.io/sky-walking/

# API Guide
http://wu-sheng.github.io/sky-walking/sample-code/codeView.html

# Contact Us

Mail: wu.sheng@foxmail.com

# Quick Start
## 编译与部署
### 部署第三方软件
- 安装zookeeper 3.4.6
- 安装apache hbase 1.1.2
- 安装mysql
- 安装tomcat 7

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
#偏移量注册文件的目录
registerpersistence.register_file_parent_directory=d:/test-data/data/offset

#hbase zk quorum
hbaseconfig.zk_hostname=10.1.235.197,10.1.235.198,10.1.235.199
#hbase zk port
hbaseconfig.client_port=29181

#Redis配置
alarm.redis_server=10.1.241.18:16379
```
- 启动服务
```shell
$cd installer/bin
$./swserver.sh
```
- 可根据需要部署多个实例

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
发布skywalking-sdk-plugin下的各子工程(dubbo-plugin，spring-plugin，web-plugin，jdbc-plugin，httpclient-4.2.x-plugin，httpclient-4.3.x-plugin)

## 根据所需的监控点，引入maven依赖
暂不存在公网仓库，需要本地编译并发布
```xml
<!-- 监控api，可监控插件不支持的调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-api</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<!-- Spring插件，监控所有Spring托管对象的调用-->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-spring-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<!-- dubbo插件，监控dubbo/dubbox调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-dubbo-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<!-- jdbc插件，监控所有的jdbc调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-jdbc-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<!-- web，监控web调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-web-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<!-- httpClient插件，监控httpClient 4.2的调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-httpClient-4.2.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<!-- httpClient插件，监控httpClient 4.3的调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-httpClient-4.3.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
查询不会引用所需的第三方组件（如Spring、dubbo、dubbox等），请自行引入所需的版本。

## 根据所需插件配置应用程序
参考[用户指南](http://wu-sheng.github.io/sky-walking/sample-code/codeView.html)

## 下载并设置授权文件
- 注册并登陆过skywalking-webui，创建应用。（一个用户代表一个逻辑集群，一个应用代表一个服务集群。如前后端应用应该设置两个应用，但归属一个用户）
- 下载授权文件，并在运行时环境中，将授权文件加入到CLASSPATH中

## 在运行时环境中设置环境变量
```
export SKYWALKING_RUN=true
```
- 设置完成后，可以在当前环境中启动业务应用系统

## 通过扩展log在应用日志中，显示trace-id
- 编译并发布skywalking-log/log4j-1.x-plugin和skywalking-log/log4j-2.x-plugin
- 引用所需的日志插件
```xml
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-log4j-1.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-log4j-2.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 设置log4j和log4j2参考[用户指南](http://wu-sheng.github.io/sky-walking/sample-code/codeView.html)->能通过哪些渠道在应用中发现traceid?
- 日志示例
```
#tid:N/A，代表环境设置不正确或监控已经关闭
#tid: ,代表测试当前访问不在监控范围
#tid:1.0a2.1453065000002.c3f8779.27878.30.184，标识此次访问的tid信息，示例如下
[DEBUG] Returning handler method [public org.springframework.web.servlet.ModelAndView com.ai.cloud.skywalking.example.controller.OrderSaveController.save(javax.servlet.http.HttpServletRequest)] TID:1.0a2.1453192613272.2e0c63e.11144.58.1 2016-01-19 16:36:53.288 org.springframework.beans.factory.support.DefaultListableBeanFactory 
```
