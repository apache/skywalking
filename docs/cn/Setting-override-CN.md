# 覆盖配置
## 版本支持
5.0.0-beta + 

_探针的覆盖配置从  3.2.5版本就已经支持_

## 什么是覆盖配置?
默认情况下, SkyWalking 探针读取`agent.config` 配置文件, 服务端读取配置文件 `application.yml` . 
覆盖配置表示用户可以通过启动参数(-D)来覆盖这些配置文件里面的配置.

## 配置优先级
启动参数配置(-D) > 配置文件
 
## 覆盖
### 探针
使用 `skywalking.` + key 的格式进行配置,覆盖配置文件中的配置.

- 为什么需要这个前缀?
   探针和目标应用共享系统启动参数(环境)的配置,使用这个前缀可以避免变量冲突.  
### Collector
使用配置文件中相同的 key ,在启动参数中覆盖`collector`中的配置.
例如:
-  `application.yml`的配置:
```yaml
agent_gRPC:
  gRPC:
    host: localhost
    port: 11800
```

- 在启动脚本中使用如下启动参数配置将端口设置为31200.
```
-Dagent_gRPC.gRPC.port=31200
```