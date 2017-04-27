package org.skywalking.apm.plugin.jdbc.connectionurl.parser;

import org.skywalking.apm.plugin.jdbc.ConnectionInfo;

public interface ConnectionURLParser {
    /**
     * {@link ConnectionURLParser} parses database name and the database host(s) from connection url.
     *
     * @return connection info.
     */
    ConnectionInfo parse();
}
