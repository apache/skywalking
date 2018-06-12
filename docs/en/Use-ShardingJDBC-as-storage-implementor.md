# Supported Database Sharding Storage
Beside the default Elasticsearch storage, the user can use shardingJDBC and MySQL as storage implementor.
Note: it only supported MYSQL database sharding, and due to the license restrictions, the users need to import MYSQL Driver manually.

## Requirement
- Manually import MySQL Driver package mysql-connector-java-5.1.36.jar to collector libs directory.
- In config/application.yml, delete the ElasticSearch configuration and add the following shardingjdbc configuration.
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

## See also
- ShardingJDBC now named as ShardingSphere since its 3.x, see [here](http://shardingsphere.io)
