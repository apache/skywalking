# Virtual Database

Virtual databases represent the database nodes detected by [server agents' plugins](server-agents.md). The performance
metrics of the databases are also from the Database client-side perspective.

For example, JDBC plugins(MySQL, PostgreSQL, MariaDB, MSSQL) in the Java agent could detect the latency of SQL
performance and SQL statements. As a result, SkyWalking would show database traffic, latency, success rate, and sampled slow SQLs powered by backend analysis capabilities in this dashboard.

The Database access span should have
- It is an **Exit** span
- **Span's layer == DATABASE**
- Tag key = `db.statement`, value = SQL statement
- Tag key = `db.type`, value = the type of Database
- Span's peer is the network address(IP or domain) of Database server.

Ref [slow cache doc](../backend/slow-db-statement.md) to know more slow SQL settings.
