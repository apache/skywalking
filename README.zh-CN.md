[English](./README.md) | 简体中文

#### 快速入门
1. 需要JDK8+
2. 通过命令下载应用程序，或直接前往[发布页面](https://github.com/wu-sheng/sky-walking-ui/releases)下载

```shell
> curl -O https://github.com/wu-sheng/sky-walking-ui/releases/download/<project-version>/skywalking-web.tar.gz
> tar -xvf skywalking-web.tar.gz
> cd skywalking-web/bin
> ./startup.sh
```

3. 配置应用程序
* 在`config/application.properties`文件中，设置webui的监听端口
* 在`config/collector_config.properties`文件中，设置collector集群或单实例地址，地址对应的是 `skywalking-collector` 项目中配置文件 `config/application.yml` 中配置项 `naming.jetty.host` 和 `naming.jetty.port`

4. 浏览器打开 `http://127.0.0.1:8080/`