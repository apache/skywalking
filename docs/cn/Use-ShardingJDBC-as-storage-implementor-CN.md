# 支持数据库分片存储
除了默认的Elasticsearch存储外，用户可以用shardingJDBC结合MySQL作为存储实现。
注意：目前仅支持MYSQL数据库的分片存储，且由于license限制，需要用户手动引入mysql驱动包。

## 配置要求
- 手工导入MYSQL的驱动包mysql-connector-java-5.1.36.jar到collector libs目录下。
- config/application.yml中，删除Elasticsearch配置，添加shardingjdbc配置如下。
```
  storage:
    shardingjdbc:
      driverClass: com.mysql.jdbc.Driver
      # JDBC Datasource connections for ShardingJDBC, multiple should be separated by comma
      url: jdbc:mysql://ip1:port1/skywalking,jdbc:mysql://ip2:port2/skywalking
      # Usernames, which match the sequence of Datasource URLs
      userName: admin,admin
      # Passwords, which match the sequence of Datasource URLs
      password: 123456,123456
```

## 参阅
- ShardingJDBC从3.x起，现已更名为ShardingSphere，[点这](http://shardingsphere.io)
