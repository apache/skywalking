### 现象
1. 加载探针并启动应用
2. Console中被GRPC日志刷屏

### 原因
Skywalking采用了GRPC框架发送数据，GRPC框架读取log的配置文件进行日志输出。

### 解决方法
在log的配置文件中添加对`org.apache.skywalking.apm.dependencies`包的过滤
