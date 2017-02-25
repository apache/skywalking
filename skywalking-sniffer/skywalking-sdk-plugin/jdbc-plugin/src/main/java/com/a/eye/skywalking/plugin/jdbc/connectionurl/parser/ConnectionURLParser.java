package com.a.eye.skywalking.plugin.jdbc.connectionurl.parser;

import com.a.eye.skywalking.plugin.jdbc.ConnectionInfo;


public interface ConnectionURLParser {
    /**
     * {@link ConnectionURLParser} parses database name and the database host(s) from connection url.
     *
     * @return connection info.
     */
    ConnectionInfo parse();
}
