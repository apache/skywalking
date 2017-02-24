package com.a.eye.skywalking.plugin.jdbc.connectionurl.parser;

import com.a.eye.skywalking.plugin.jdbc.ConnectionInfo;

/**
 * {@link URLParser#parser(String)} support parse the connection url, such as Mysql, Oracle, H2 Database.
 * But there are some url cannot be parsed, such as Oracle connection url with multiple host.
 *
 * @author zhangxin
 */
public class URLParser {
    public static ConnectionInfo parser(String url) {
        ConnectionURLParser parser = null;
        if (url.startsWith("jdbc:mysql")) {
            parser = new MysqlURLParser(url);
        } else if (url.startsWith("jdbc:oracle")) {
            parser = new OracleURLParser(url);
        } else if (url.startsWith("jdbc:h2")) {
            parser = new H2URLParser(url);
        }
        return parser.parse();
    }
}
