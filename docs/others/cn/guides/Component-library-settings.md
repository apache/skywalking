# 组件库设置
组件库设置与受监视应用程序中使用的您自己或第三方库有关。

在 agent 或 SDK 中, 无论将库名称收集为ID或String（简单地说，例如SpringMVC），收集器格式化ID中的数据，以获得更好的性能和更少的存储要。

此外，收集器根据组件库推测远程服务，例如：
组件库是MySQL驱动程序库，那么远程服务应该是MySQL Server。

出于这两个原因，收集器需要此文件中的两部分设置：

1.组件库ID，名称和语言。
1.远程服务器映射，基于本地库。

**必须在此文件中定义所有组件名称和ID。**

## 组件库ID
定义受监视应用程序中使用的所有组件库的名称和ID。
这是双向映射，agent或SDK可以value（ID）来表示上行链路数据中的组件名称。

- Name：agent和UI中使用的组件名称
- id：唯一ID。 一旦发布，所有ID都会被保留。
- languages：程序语言可以使用此组件。 多语言应该用`，`分隔

### ID 规则
- Java and 多语言共享: (0, 3000]
- .NET 平台使用: (3000, 4000]
- Node.js 平台使用: (4000, 5000]
- Go 平台使用: (5000, 6000]
- PHP 平台使用: (6000, 7000]
- Python 使用: (7000, 8000]

实例
```yaml
Tomcat:
  id: 1
  languages: Java
HttpClient:
  id: 2
  languages: Java,C#,Node.js
Dubbo:
  id: 3
  languages: Java
H2:
  id: 4
  languages: Java
```

## 远端服务映射
远端服务器根据本地组件推断而出，映射基于组件库名称.

- Key: 客户端组件库名称
- Value: 服务组件名称

```yaml
Component-Server-Mappings:
  Jedis: Redis
  StackExchange.Redis: Redis
  Redisson: Redis
  Lettuce: Redis
  Zookeeper: Zookeeper
  SqlClient: SqlServer
  Npgsql: PostgreSQL
  MySqlConnector: Mysql
  EntityFrameworkCore.InMemory: InMemoryDatabase
```