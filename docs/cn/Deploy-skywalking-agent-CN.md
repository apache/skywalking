## 下载skywalking探针发布版本
- 前向[发布页面](https://github.com/apache/incubator-skywalking/releases)

## 部署探针
1. 拷贝skywalking-agent目录到所需位置，探针包含整个目录，请不要改变目录结构
1. 增加JVM启动参数，`-javaagent:/path/to/skywalking-agent/skywalking-agent.jar`。参数值为skywalking-agent.jar的绝对路径。

新目录结构如下：
```
+-- skywalking-agent
    +-- activations
         apm-toolkit-log4j-1.x-activation.jar
         apm-toolkit-log4j-2.x-activation.jar
         apm-toolkit-logback-1.x-activation.jar
         ...
    +-- config
         agent.config  
    +-- plugins
         apm-dubbo-plugin.jar
         apm-feign-default-http-9.x.jar
         apm-httpClient-4.x-plugin.jar
         .....
    skywalking-agent.jar
```

- `/config/agent.config`包含探针所需配置，中文说明如下。

```properties
# 当前的应用编码，最终会显示在webui上。
# 建议一个应用的多个实例，使用有相同的application_code。请使用英文
agent.application_code=Your_ApplicationName

# 每三秒采样的Trace数量
# 默认为负数，代表在保证不超过内存Buffer区的前提下，采集所有的Trace
# agent.sample_n_per_3_secs=-1

# 设置需要忽略的请求地址
# 默认配置如下
# agent.ignore_suffix=.jpg,.jpeg,.js,.css,.png,.bmp,.gif,.ico,.mp3,.mp4,.html,.svg

# 探针调试开关，如果设置为true，探针会将所有操作字节码的类输出到/debugging目录下
# skywalking团队可能在调试，需要此文件
# agent.is_open_debugging_class = true

# 对应Collector的config/application.yml配置文件中 agent_server/jetty/port 配置内容
# 例如：
# 单节点配置：SERVERS="127.0.0.1:8080" 
# 集群配置：SERVERS="10.2.45.126:8080,10.2.45.127:7600" 
collector.servers=127.0.0.1:10800

# 日志文件名称前缀
logging.file_name=skywalking-agent.log

# 日志文件最大大小
# 如果超过此大小，则会生成新文件。
# 默认为300M
logging.max_file_size=314572800

# 日志级别，默认为DEBUG。
logging.level=DEBUG
```

- 启动被监控应用。

# 高级特性
- 插件会被统一放置在`plugins`目录中，新的插件，也只需要在启动阶段，放在目录中，就自动生效。删除则失效。
- 配置除了通过`/config/agent.config`文件外，可以通过环境变量和VM参数（-D）来进行设置
  - 参数的key = `skywalking.` + `agent.config`文件中的key
  - 优先级：系统环境变量 > VM参数（-D） > `/config/agent.config`中的配置
- Log默认使用文件输出，输出到`/logs`目录中

# Tomcat配置探针FAQ
- Tomcat 7
修改`tomcat/bin/catalina.sh`，在首行加入如下信息
```shell
CATALINA_OPTS="$CATALINA_OPTS -javaagent:/path/to/skywalking-agent/skywalking-agent.jar"; export CATALINA_OPTS
```

- Tomcat 8
修改`tomcat/bin/catalina.sh`，在首行加入如下信息
```shell
set "CATALINA_OPTS=... -javaagent:E:\apache-tomcat-8.5.20\skywalking-agent\skywalking-agent.jar"
```