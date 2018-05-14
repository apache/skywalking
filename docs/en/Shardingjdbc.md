# Supported Database Sharding Storage
Beside the default Elasticsearch storage, it also support the database sharding storage, it allow the users to store data in multiple databases.
Note: it only supported MYSQL database sharding, and due to the license restrictions, the users need to import MYSQL Driver manually.

## Supported version
5.0.0-beta +

## Requirement
- Manually import MySQL Driver package mysql-connector-java-5.1.36.jar to collector-libs directory.
- In config/application.yml, close the elasticsearch configuration and open the shardingjdbc configuration, multiple data source configurations should be separated by Half Comma.

## See also
- [shardingsphere website](http://shardingsphere.io)