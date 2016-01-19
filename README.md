# Sky Walking
SkyWalking-Distributed Application Tracing System, 是一个对JAVA应用程序运行情况进行追踪、告警和分析的系统。
* 核心理论为[Google Dapper论文：Dapper, a Large-Scale Distributed Systems Tracing Infrastructure](http://research.google.com/pubs/pub36356.html),英语有困难的同学可参考[国内翻译](http://duanple.blog.163.com/blog/static/70971767201329113141336/)
* 本分析系统能通过不修改或少量修改代码的模式，对现有的JAVA应用或J2EE应用进行监控和数据收集，并针对应用进场进行准实时告警。此外提供大量的调用性能分析功能，解决目前的监控系统主要监控进程、端口而非应用实际性能的问题。

# 主要贡献者
* 吴晟 &nbsp;&nbsp;[亚信](http://www.asiainfo.com/) wusheng@asiainfo.com
* 张鑫 &nbsp;&nbsp;[亚信](http://www.asiainfo.com/) zhangxin10@asiainfo.com

# 整体架构图
![整体架构图](http://wu-sheng.github.io/sky-walking/sample-code/images/skywalkingClusterDeploy.jpeg)

# 追踪链路图
![追踪连路途](http://wu-sheng.github.io/sky-walking/sample-code/images/traceLogView.jpeg)

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

### 编译安装SkyWalking Alarm


### 编译安装SkyWalking Analysis
暂未提供

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
通过skywalking-webui工程下载授权文件，并在运行时环境中，将授权文件加入到CLASSPATH中

## 在运行时环境中设置环境变量
export SKYWALKING_RUN=true
