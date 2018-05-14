# 支持数据库分片存储
除了默认的Elasticsearch存储外，还支持数据库分片存储，允许用户将数据存储在多个数据库中。
注意：目前仅支持MYSQL数据库的分片存储，且由于license限制，需要用户手动引入mysql驱动包。

## 版本支持
5.0.0-beta +

## 配置要求
- 手工导入MYSQL的驱动包mysql-connector-java-5.1.36.jar到collector libs目录下。
- config/application.yml中，关闭elasticsearch配置，打开shardingjdbc配置，多个数据源配置用半角逗号隔开。
  storage:
    shardingjdbc:
      driverClass: com.mysql.jdbc.Driver
      url: jdbc:mysql://ip1:port1/skywalking,jdbc:mysql://ip2:port2/skywalking
      userName: admin,admin
      password: 123456,123456

## 参阅
- [shardingsphere官网](http://shardingsphere.io)
