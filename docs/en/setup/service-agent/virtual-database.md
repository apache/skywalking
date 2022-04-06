# Virtual Database

Virtual databases represents the database nodes detected by [server agents' plugins](server-agents.md). The performance
metrics of the databases are also from Database client side perspective.

For example, JDBC plugins(MySQL, PostgreSQL, Mariadb, MSSQL) in the Java agent could detect the latency of SQL
performance, as well as SQL statements. As a result, in this dashboard, SkyWalking would show database traffic, latency,
success rate and sampled slow SQLs powered by backend analysis capabilities.

The Database access span should have
- It is an **Exit** span
- **Span's layer == DATABASE**
- Tag key = `db.statement`, value = SQL statement
- Tag key = `db.type`, value = the type of Database
- Span's peer is the network address(IP or domain) of Database server.
