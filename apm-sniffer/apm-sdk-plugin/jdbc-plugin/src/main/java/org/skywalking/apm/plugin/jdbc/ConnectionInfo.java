package org.skywalking.apm.plugin.jdbc;

import org.skywalking.apm.network.trace.component.OfficialComponent;

/**
 * {@link ConnectionInfo} stored the jdbc connection info, the connection info contains db type, host, port, database
 * name. The {@link #hosts} be null if {@link #host} is not null.
 *
 * @author zhangxin
 */
public class ConnectionInfo {
    /**
     * DB type, such as mysql, oracle, h2.
     */
    private final String dbType;
    /**
     * Database host name.
     */
    private String host;
    /**
     * Database port.
     */
    private int port;
    /**
     * Operation database name.
     */
    private final String databaseName;
    /**
     * Database hosts.
     */
    private String hosts;

    /**
     * Component
     */
    private final OfficialComponent component;

    public ConnectionInfo(OfficialComponent component, String dbType, String host, int port, String databaseName) {
        this.dbType = dbType;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.component = component;
    }

    public ConnectionInfo(OfficialComponent component, String dbType, String hosts, String databaseName) {
        this.dbType = dbType;
        this.hosts = hosts;
        this.databaseName = databaseName;
        this.component = component;
    }

    public String getDBType() {
        return dbType;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getHosts() {
        return hosts;
    }

    public OfficialComponent getComponent() {
        return component;
    }

}
