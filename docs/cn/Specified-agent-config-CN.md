# 自定义探针配置文件路径

## 版本支持

5.0.0-RC+

## 什么是自定义Agent配置文件路径? 
默认情况下, SkyWalking 探针读取与 `SkyWalking-agent.jar` 同目录级别下的 `config` 目录下的 `agent.config` 配置文件。
用户可以自定义探针配置文件的路径,让探针监控的每个服务都是用其特有文件目录的探针配置，用来统一管理一台物理机器上面的多个不同运行服务的探针配置。此自定义探针配置文件与[通过系统启动参数进行覆盖配置](Setting-override-CN.md)无任何冲突。

## 配置优先级
自定义探针配置文件 > 默认的配置文件
 
## 自定义探针配置路径
> 自定义的探针配置文件内容格式必须与默认探针配置文件内容格式一致，这里所改变的仅仅只是配置文件的路径

#### 使用时机：在部署JavaAgent增加JVM启动参数的时候
#### 使用方式：<br>
     增加JVM启动参数`-javaagent:/path/to/skywalking-agent/skywalking-agent.jar=/path/to/agent.config`
`=`之前的`/path/to/skywalking-agent/skywalking-agent.jar` 为 `skywalking-agent.jar`的绝对路径<br>
`=`之后的`/path/to/agent.config`为探针自定义配置文件的绝对路径。
>如果依旧使用默认的探针配置文件的路径，则JVM启动参数为 `-javaagent:/path/to/skywalking-agent/skywalking-agent.jar` 


  
