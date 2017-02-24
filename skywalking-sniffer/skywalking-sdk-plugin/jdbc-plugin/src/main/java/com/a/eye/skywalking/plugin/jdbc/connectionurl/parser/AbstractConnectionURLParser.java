package com.a.eye.skywalking.plugin.jdbc.connectionurl.parser;

/**
 * {@link AbstractConnectionURLParser} is abstract class that the class that parse jdbc url.
 *
 * @author zhangxin
 */
public abstract class AbstractConnectionURLParser implements ConnectionURLParser {

    /**
     * Connection url
     */
    protected String url;

    public AbstractConnectionURLParser(String url) {
        this.url = url;
    }

    /**
     * Fetch the index range that database host and port from connection url.
     *
     * @return index range that database hosts.
     */
    protected abstract int[] fetchDatabaseHostsIndexRange();

    /**
     * Fetch the index range that database name from connection url.
     *
     * @return index range that database name.
     */
    protected abstract int[] fetchDatabaseNameIndexRange();

    /**
     * Fetch database host(s) from connection url.
     *
     * @return database host(s).
     */
    protected String fetchDatabaseHostsFromURL() {
        int[] indexRange = fetchDatabaseHostsIndexRange();
        return url.substring(indexRange[0], indexRange[1]);
    }

    /**
     * Fetch database name from connection url.
     *
     * @return database name.
     */
    protected String fetchDatabaseNameFromURL() {
        int[] indexRange = fetchDatabaseNameIndexRange();
        return url.substring(indexRange[0], indexRange[1]);
    }

    /**
     * Fetch database name from connection url.
     *
     * @return database name.
     */
    protected String fetchDatabaseNameFromURL(int[] indexRange) {
        return url.substring(indexRange[0], indexRange[1]);
    }

    public String getConnectionURL() {
        return this.url;
    }
}
