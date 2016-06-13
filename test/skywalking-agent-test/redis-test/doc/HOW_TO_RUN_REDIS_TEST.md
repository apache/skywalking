#Redis agent test project

##目标 Target
主要是为了测试Redis插件在agent模式下是否正常工作. Redis agent test project  test redis plugin work well on agent model

##结构 
Redis agent test project主要包含Redis的两个功能的测试. Redis agent test project contain two function test
1. 单机的Redis的测试. Single redis model
2. 集群的Redis的测试. Cluster redis model

##测试环境搭建 Build test environment
###安装单机版的Redis  Install single redis 
1. 下载 Download
[下载地址](http://download.redis.io/releases/redis-3.2.0.tar.gz)  [Download Link](http://download.redis.io/releases/redis-3.2.0.tar.gz)
2. 解压 Unpack
```shell
$> tar -cvf redis-3.2.0.tar.gz
```
3. 运行下列命令 Run the command as follow
```shell
$> cd REDIS_HOME
$> ./src/redis-server ../redis.conf
```

###安装集群版的Redis Install the cluster redis
1. 下载 Download
     [下载地址](http://download.redis.io/releases/redis-3.2.0.tar.gz)  [Download Link](http://download.redis.io/releases/redis-3.2.0.tar.gz)
2. 解压 Unpack
```shell
$> tar -cvf redis-3.2.0.tar.gz
```
3. 创建并启动集群节点 Create and run cluster node
例如创建一个7000端口的节点,需要根据实际情况修改redis.conf参数, For example create cluster node that the port is 7000, and 
Redis.conf need to modify the parameters according to the actual situation
```
$>cd REDIS_HOME
$>mkdir -p cluster/7000 
$>cp redis.conf cluster/7000
$> cd cluster/7000/
$> ../../src/redis-server redis.conf
```
4. 启动集群 Start the cluster
```shell
redis-trib.rb create --replicas 1 127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 127.0.0.1:7003 127.0.0.1:7004 127.0.0.1:7005
```

## 启动测试 Run test case
启动测试用例时，VM参数中需要添加以下的vm参数。**注意**:由于buffer在JVM是独一份，为了避免测试结果不一致，测试类不能一起测试，
只能单独运行测试方法，测试中的环境参数可能跟随机器变化，所以可能需要进行修改。
When you start the test case, VM parameters need to add the following parameters vm. **Notice** 
Because the buffer is the only one in JVM , in order to avoid inconsistent results, test class can not be tested together,
Can only run a single test method, the test environment parameters may change following the machine, it may need to be modified.
```
-javaagent:${SKYWALKING_AGENT_TEST_PROJECT_PATH}/lib/skywalking-agent-1.0-Final.jar
```