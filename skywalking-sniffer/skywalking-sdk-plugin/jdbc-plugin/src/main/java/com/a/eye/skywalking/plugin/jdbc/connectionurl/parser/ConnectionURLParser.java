package com.a.eye.skywalking.plugin.jdbc.connectionurl.parser;

import com.a.eye.skywalking.plugin.jdbc.ConnectionInfo;


public interface ConnectionURLParser {
    /**
     * The class that extend {@link ConnectionURLParser} spilt
     * the database name , the database host(s) from connection url.
     *
     * @return connection info.
     */
    ConnectionInfo parse();
}
