- [下载Source code](https://github.com/apache/incubator-skywalking/releases)并解压，进入解压目录，执行以下命令：

```shell
> docker-compose pull
> docker-compose up
```
- 探针配置的端口是10800。
- 通过http://localhost:8080 访问WebUI

注意：Docker Compose主要用于在本地进行快速环境搭建和测试，请不要通过远程访问默认Docker Compose启动的Collector环境

___
测试环境： docker 17.03.1-ce， docker compose 1.11.2
