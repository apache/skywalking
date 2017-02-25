package com.a.eye.skywalking.plugin.jdbc.connectionurl.parser;

import com.a.eye.skywalking.plugin.jdbc.ConnectionInfo;

/**
 * {@link URLParser#parser(String)} support parse the connection url, such as Mysql, Oracle, H2 Database.
 * But there are some url cannot be parsed, such as Oracle connection url with multiple host.
 *
 * @author zhangxin
 */
public class URLParser {

    private static final String MYSQL_JDBC_URL_PREFIX = "jdbc:mysql";
    private static final String ORACLE_JDBC_URL_PREFIX = "jdbc:oracle";
    private static final String H2_JDBC_URL_PREFIX = "jdbc:h2";

    public static ConnectionInfo parser(String url) {
        ConnectionURLParser parser = null;
        if (url.startsWith(MYSQL_JDBC_URL_PREFIX)) {
            parser = new MysqlURLParser(url);
        } else if (url.startsWith(ORACLE_JDBC_URL_PREFIX)) {
            parser = new OracleURLParser(url);
        } else if (url.startsWith(H2_JDBC_URL_PREFIX)) {
            parser = new H2URLParser(url);
        }
        return parser.parse();
    }
}
