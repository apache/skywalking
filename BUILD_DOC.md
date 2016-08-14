### 编译SkyWalking Protocol / Build SkyWalking Protocol
- 编译工程
- build
```shell
$cd github/sky-walking/skywalking-protocol
$mvn clean install -Dmaven.test.skip=true
```

### 编译SkyWalking主工程 / Build SkyWalking
```shell
$cd github/sky-walking
$mvn clean install -Dmaven.test.skip=true
```

- 从各工程目录中获取安装包
	- SkyWalking Agent: github/sky-walking/skywalking-collector/skywalking-agent/target/skywalking-agent-1.0-Final.jar
	- SkyWalking Server: github/sky-walking/skywalking-server/target/installer
	- SkyWalking Alarm:  github/sky-walking/skywalking-alarm/target/installer
	- SkyWalking WebUI: github/sky-walking/skywalking-webui/skywalking.war
	- SkyWalking Analysis: github/sky-walking/skywalking-analysis/skywalking-analysis-1.0-Final.jar
		- 上传skywalking-analysis-1.0-Final.jar. Upload the skywalking-analysis-1.0-Final.jar
		- 上传start-analysis.sh到同一目录. Upload the start-analysis.sh to the same directory
		- 为start-analysis.sh创建crontable定时任务，30分钟执行一次。create crontable for shell, set cycle=30min.
