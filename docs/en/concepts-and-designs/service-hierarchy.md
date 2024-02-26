# Service Hierarchy
SkyWalking v10 introduces a new concept `Service Hierarchy` which defines the relationships of existing logically same services in various layers.
OAP will detect the services from different layers, and try to build the connections.

## Detect Service Hierarchy Connections
There 2 ways to detect the connections:
1. Automatically matching through OAP internal mechanism, no extra work is required.
2. Build the connections through specific agents.

**Note:** All the relationships and auto-matching rules should be defined in the `config/hierarchy-definition.yml` file.
If you want to customize it according to your own needs, please refer to [Service Hierarchy Configuration](service-hierarchy-configuration.md).

### Automatically Matching
| Upper layer      | Lower layer | Matching rule                                                     |
|------------------|-------------|-------------------------------------------------------------------|
| MESH             | MESH_DP     | [MESH On MESH_DP](#mesh-on-mesh_dp)                               |
| MESH             | K8S_SERVICE | [MESH On K8S_SERVICE](#mesh-on-k8s_service)                       |
| MESH_DP          | K8S_SERVICE | [MESH_DP On K8S_SERVICE](#mesh_dp-on-k8s_service)                 |
| GENERAL          | K8S_SERVICE | [GENERAL On K8S_SERVICE](#general-on-k8s_service)                 |
| GENERAL          | APISIX      | [GENERAL On APISIX](#general-on-apisix)                           |
| MYSQL            | K8S_SERVICE | [MYSQL On K8S_SERVICE](#mysql-on-k8s_service)                     |
| POSTGRESQL       | K8S_SERVICE | [POSTGRESQL On K8S_SERVICE](#postgresql-on-k8s_service)           |
| SO11Y_OAP        | K8S_SERVICE | [SO11Y_OAP On K8S_SERVICE](#so11y_oap-on-k8s_service)             |
| VIRTUAL_DATABASE | MYSQL       | [VIRTUAL_DATABASE On MYSQL](#virtual_database-on-mysql)           |
| VIRTUAL_DATABASE | POSTGRESQL  | [VIRTUAL_DATABASE On POSTGRESQL](#virtual_database-on-postgresql) |
| NGINX            | K8S_SERVICE | [NGINX On K8S_SERVICE](#nginx-on-k8s_service)                     |
| APISIX           | K8S_SERVICE | [APISIX On K8S_SERVICE](#apisix-on-k8s_service)                   |
| ROCKETMQ         | K8S_SERVICE | [ROCKETMQ On K8S_SERVICE](#rocketmq-on-k8s_service)               |
| VIRTUAL_MQ       | ROCKETMQ    | [VIRTUAL_MQ On ROCKETMQ](#virtual_mq-on-rocketmq)              |
| RABBITMQ         | K8S_SERVICE | [RABBITMQ On K8S_SERVICE](#rocketmq-on-k8s_service)               |
| VIRTUAL_MQ       | RABBITMQ    | [VIRTUAL_MQ On RABBITMQ](#virtual_mq-on-rabbitmq)              |

- The following sections will describe the **default matching rules** in detail and use the `upper-layer On lower-layer` format. 
- The example service name are based on SkyWalking [Showcase](https://github.com/apache/skywalking-showcase) default deployment.
- In SkyWalking the service name could be composed of `group` and `short name` with `::` separator.

#### MESH On MESH_DP
- Rule name: `name` 
- Groovy script: `{ (u, l) -> u.name == l.name }`
- Description: MESH.service.name == MESH_DP.service.name
- Matched Example: 
    - MESH.service.name: `mesh-svr::songs.sample-services`
    - MESH_DP.service.name: `mesh-svr::songs.sample-services`

#### MESH On K8S_SERVICE
- Rule name: `short-name`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName }`
- Description: MESH.service.shortName == K8S_SERVICE.service.shortName
- Matched Example: 
    - MESH.service.name: `mesh-svr::songs.sample-services`
    - K8S_SERVICE.service.name: `skywalking-showcase::songs.sample-services`

#### MESH_DP On K8S_SERVICE
- Rule name: `short-name`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName }`
- Description: MESH_DP.service.shortName == K8S_SERVICE.service.shortName
- Matched Example: 
    - MESH_DP.service.name: `mesh-svr::songs.sample-services`
    - K8S_SERVICE.service.name: `skywalking-showcase::songs.sample-services`

#### GENERAL On K8S_SERVICE
- Rule name: `lower-short-name-remove-ns`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.')) }`
- Description: GENERAL.service.shortName == K8S_SERVICE.service.shortName without namespace
- Matched Example: 
    - GENERAL.service.name: `agent::songs`
    - K8S_SERVICE.service.name: `skywalking-showcase::songs.sample-services`

#### GENERAL On APISIX
- Rule name: `lower-short-name-remove-ns`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.')) }`
- Description: GENERAL.service.shortName == APISIX.service.shortName without namespace
- Matched Example: 
    - GENERAL.service.name: `agent::frontend`
    - APISIX.service.name: `APISIX::frontend.sample-services`

#### MYSQL On K8S_SERVICE
- Rule name: `short-name`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName }`
- Description: MYSQL.service.shortName == K8S_SERVICE.service.shortName
- Matched Example: 
    - MYSQL.service.name: `mysql::mysql.skywalking-showcase`
    - K8S_SERVICE.service.name: `skywalking-showcase::mysql.skywalking-showcase`

#### POSTGRESQL On K8S_SERVICE
- Rule name: `short-name`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName }`
- Description: POSTGRESQL.service.shortName == K8S_SERVICE.service.shortName
- Matched Example: 
    - POSTGRESQL.service.name: `postgresql::psql.skywalking-showcase`
    - K8S_SERVICE.service.name: `skywalking-showcase::psql.skywalking-showcase`

#### SO11Y_OAP On K8S_SERVICE
- Rule name: `short-name`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName }`
- Description: SO11Y_OAP.service.shortName == K8S_SERVICE.service.shortName
- Matched Example: 
    - SO11Y_OAP.service.name: `demo-oap.skywalking-showcase`
    - K8S_SERVICE.service.name: `skywalking-showcase::demo-oap.skywalking-showcase`

#### VIRTUAL_DATABASE On MYSQL
- Rule name: `lower-short-name-with-fqdn`
- Groovy script: `{ (u, l) -> u.shortName.substring(0, u.shortName.lastIndexOf(':')) == l.shortName.concat('.svc.cluster.local') }`
- Description: VIRTUAL_DATABASE.service.shortName remove port == MYSQL.service.shortName with fqdn suffix
- Matched Example: 
    - VIRTUAL_DATABASE.service.name: `mysql.skywalking-showcase.svc.cluster.local:3306`
    - MYSQL.service.name: `mysql::mysql.skywalking-showcase`

#### VIRTUAL_DATABASE On POSTGRESQL
- Rule name: `lower-short-name-with-fqdn`
- Groovy script: `{ (u, l) -> u.shortName.substring(0, u.shortName.lastIndexOf(':')) == l.shortName.concat('.svc.cluster.local') }`
- Description: VIRTUAL_DATABASE.service.shortName remove port == POSTGRESQL.service.shortName with fqdn suffix
- Matched Example: 
    - VIRTUAL_DATABASE.service.name: `psql.skywalking-showcase.svc.cluster.local:5432`
    - POSTGRESQL.service.name: `postgresql::psql.skywalking-showcase`

#### NGINX On K8S_SERVICE
- Rule name: `short-name`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName }`
- Description: NGINX.service.shortName == K8S_SERVICE.service.shortName
- Matched Example: 
    - NGINX.service.name: `nginx::nginx.skywalking-showcase`
    - K8S_SERVICE.service.name: `skywalking-showcase::nginx.skywalking-showcase`

#### APISIX On K8S_SERVICE
- Rule name: `short-name`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName }`
- Description: APISIX.service.shortName == K8S_SERVICE.service.shortName
- Matched Example: 
    - APISIX.service.name: `APISIX::frontend.sample-services`
    - K8S_SERVICE.service.name: `skywalking-showcase::frontend.sample-services`

#### ROCKETMQ On K8S_SERVICE
- Rule name: `short-name`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName }`
- Description: ROCKETMQ.service.shortName == K8S_SERVICE.service.shortName
- Matched Example:
  - ROCKETMQ.service.name: `rocketmq::rocketmq.skywalking-showcase`
  - K8S_SERVICE.service.name: `skywalking-showcase::rocketmq.skywalking-showcase`

#### VIRTUAL_MQ On ROCKETMQ
- Rule name: `lower-short-name-with-fqdn`
- Groovy script: `{ (u, l) -> u.shortName.substring(0, u.shortName.lastIndexOf(':')) == l.shortName.concat('.svc.cluster.local') }`
- Description: VIRTUAL_MQ.service.shortName remove port == ROCKETMQ.service.shortName with fqdn suffix
- Matched Example:
  - VIRTUAL_MQ.service.name: `rocketmq.skywalking-showcase.svc.cluster.local:9876`
  - ROCKETMQ.service.name: `rocketmq::rocketmq.skywalking-showcase`

#### RABBITMQ On K8S_SERVICE
- Rule name: `short-name`
- Groovy script: `{ (u, l) -> u.shortName == l.shortName }`
- Description: RABBITMQ.service.shortName == K8S_SERVICE.service.shortName
- Matched Example:
  - RABBITMQ.service.name: `rabbitmq::rabbitmq.skywalking-showcase`
  - K8S_SERVICE.service.name: `skywalking-showcase::demo-rabbitmq.skywalking-showcase`

#### VIRTUAL_MQ On RABBITMQ
- Rule name: `lower-short-name-with-fqdn`
- Groovy script: `{ (u, l) -> u.shortName.substring(0, u.shortName.lastIndexOf(':')) == l.shortName.concat('.svc.cluster.local') }`
- Description: VIRTUAL_MQ.service.shortName remove port == RABBITMQ.service.shortName with fqdn suffix
- Matched Example:
  - VIRTUAL_MQ.service.name: `rabbitmq.skywalking-showcase.svc.cluster.local:5672`
  - RABBITMQ.service.name: `rabbitmq::rabbitmq.skywalking-showcase`

### Build Through Specific Agents
Use agent tech involved(such as eBPF) and deployment tools(such as operator and agent injector) detect the service hierarchy relations.

| Upper layer | Lower layer  | Agent |
|-------------|--------------|-------|


# Instance Hierarchy
Instance Hierarchy relationship follows the same definition as Service Hierarchy.

### Automatically Matching
If the service hierarchy is built, the instance hierarchy relationship could be detected automatically through 
the following rules:
1. The upper instance name equals the lower instance name.
2. The upper instance attribute `pod/hostname` equals the lower instance attribute `pod/hostname`.
3. The upper instance attribute `pod/hostname` equals the lower instance name.
4. The upper instance name equals the lower instance attribute `pod/hostname`.

### Build Through Specific Agents
