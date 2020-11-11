# Component library settings
Component library settings are about your own or 3rd part libraries used in monitored application.

In agent or SDK, no matter library name collected as ID or String(literally, e.g. SpringMVC), collector
formats data in ID for better performance and less storage requirements. 

Also, collector conjectures the remote service based on the component library, such as: 
the component library is MySQL Driver library, then the remote service should be MySQL Server. 

For those two reasons, collector require two parts of settings in this file:
1. Component Library id, name and languages.
1. Remote server mapping, based on local library.

**All component names and IDs must be defined in this file.**

## Component Library id
Define all component libraries' names and IDs, used in monitored application.
This is a both-way mapping, agent or SDK could use the value(ID) to represent the component name in uplink data.

- Name: the component name used in agent and UI
- id: Unique ID. All IDs are reserved, once it is released.
- languages: Program languages may use this component. Multi languages should be separated by `,`

### ID rules
- Java and multi languages shared: (0, 3000)
- .NET Platform reserved: [3000, 4000)
- Node.js Platform reserved: [4000, 5000)
- Go reserved: [5000, 6000)
- Lua reserved: [6000, 7000)
- Python reserved: [7000, 8000)
- PHP reserved: [8000, 9000)
- C++ reserved: [9000, 10000)

Example
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

## Remote server mapping
Remote server will be conjectured by the local component. The mappings are based on Component library names.

- Key: client component library name
- Value: server component name

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
