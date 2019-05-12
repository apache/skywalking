# Backend Inventory Entity Extension
SkyWalking includes four inventory entities.
- Service Inventory
- Service Instance Inventory
- Endpoint Inventory
- Network Address Inventory

All metrics, topology, trace and alarm are related to these entity IDs. 

For understanding the **Service**, **Service Instance** and **Endpoint** concepts,
please read [Project Overview](../concepts-and-designs/overview.md#why-use-skywalking).

For **Network Address Inventory**, it represents all network address, in IP:port, hostname, domain name
formats, which are detected by language agents or other probes.

## Extension
Right now, only **Service Inventory** extension is already supported in backend core.
Service provides field `properties` in Json format, which is usually used for specific service 
rather than normal business services, such as Database, Cache, MQ, etc.

For keeping code consistent and friendly in query and visualization, the Json properties
need to follow the rules.

### Database
1. NodeType == **Database(1)**
1. Json properties include following keys.
  - `database`. Database name, such as MySQL, PostgreSQL
  - `db.type`. Database type, such as sql db, redis db.
  - `db.instance`. Database instance name.


