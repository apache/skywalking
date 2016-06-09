### 编译安装SkyWalking Server / Build SkyWalking Server
- 编译工程
- build
```shell
$cd github/sky-walking/skywalking-server
$mvn package -Dmaven.test.skip=true
$cd github/sky-walking/skywalking-server/target/installer
```
- 拷贝installer到服务器
- upload installer to server


### 编译安装SkyWalking Alarm / Build SkyWalking Alarm
- 编译工程
- build
```shell
$cd github/sky-walking/skywalking-alarm
$mvn package -Dmaven.test.skip=true
$cd github/sky-walking/skywalking-alarm/target/installer
```
- 拷贝installer到服务器
- upload installer to server


### 编译安装SkyWalking WebUI / Build SkyWalking WebUI

- 编译工程
- build
```shell
$cd github/sky-walking/skywalking-webui
$mvn package
```

- 上传war包到服务器，启动Tomcat服务器
- startup tomcat of webui

### 编译安装SkyWalking Analysis / Build SkyWalking Analysis
- 编译工程
- build
```shell
$cd github/sky-walking/skywalking-analysis
$mvn package -Dmaven.test.skip=true
```

- 上传skywalking-analysis-1.0-Final.jar. Upload the skywalking-analysis-1.0-Final.jar
- 上传start-analysis.sh. Upload the start-analysis.sh


## 使用maven发布各插件工程 / build and deploy plugins
- build and deploy skywalking-sdk-plugin(dubbo-plugin，spring-plugin，web-plugin，jdbc-plugin，httpclient-4.2.x-plugin，httpclient-4.3.x-plugin, etc.)
- 请跳过maven.test环节，避免打包失败
```properties
-Dmaven.test.skip=true
```
