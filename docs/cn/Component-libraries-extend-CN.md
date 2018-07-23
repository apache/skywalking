# 组件库设置
组件库设置的作用是将自定义或第三方库添加到组件库中,并监听应用程序

在agent和SDK中,ID或者name都可以定义库的身份标识(例如 SpringMVC),不过collector对ID类型做了格式化数据处理，性能方面有所提高，存储占用更少

此外,collector会根据组件库的依赖包去推测远程服务名称,例如: 若MySQL驱动库作为组件库,那collector会推测远程服务为Mysql服务

针对这两个原因,collector需要在文件中添加两个地方的配置
1. 组件库ID,名称和开发语言.
1. 基于本地库的远程服务映射.

**必须要在文件中定义所有组件名称和ID**

## 组件库id
被监控的应用程序中所定义的组件库的名称和ID,它们提供了双向映射,在agent或者SDK可以使用ID来表示上行链路数据的组件名称.

- name: 组件名称
- id: 唯一标识
- languages: 组件的开发语言,若包含多个开发语言,请用`,`分割

### ID的保留规则
- Java及多语言共享区间: (0, 3000]
- .NET平台保留区间: (3000, 4000]
- Node.js保留区间: (4000, 5000]
- Go 保留区间: (5000, 6000]
- PHP 保留区间: (6000, 7000]

示例
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

## 远程服务映射

本地组件可以推荐出远程服务,而这种映射关系是基于组件名称.

- Key: 客户端组件库名称
- Value: 服务端组件库名称

```yaml
Component-Server-Mappings:
  Jedis: Redis
  StackExchange.Redis: Redis
  SqlClient: SqlServer
  Npgsql: PostgreSQL
  MySqlConnector: Mysql
  EntityFrameworkCore.InMemory: InMemoryDatabase
```