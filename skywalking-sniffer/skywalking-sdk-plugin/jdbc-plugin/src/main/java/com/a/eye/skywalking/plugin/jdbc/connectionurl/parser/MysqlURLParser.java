package com.a.eye.skywalking.plugin.jdbc.connectionurl.parser;

import com.a.eye.skywalking.plugin.jdbc.ConnectionInfo;

/**
 * {@link MysqlURLParser} parse connection url of mysql.
 *
 * The {@link ConnectionInfo#host} be set the string between charset "//" and the first
 * charset "/" after the charset "//", and {@link ConnectionInfo#databaseName} be set the
 * string between the last index of "/" and the first charset "?". but one more thing, the
 * {@link ConnectionInfo#hosts} be set if the host container multiple host.
 *
 * @author zhangxin
 */
public class MysqlURLParser extends AbstractURLParser {

    private static final int DEFAULT_PORT = 3306;
    private static final String DB_TYPE = "Mysql";

    public MysqlURLParser(String url) {
        super(url);
    }

    @Override
    protected int[] fetchDatabaseHostsIndexRange() {
        int hostLabelStartIndex = url.indexOf("//");
        int hostLabelEndIndex = url.indexOf("/", hostLabelStartIndex + 2);
        return new int[]{hostLabelStartIndex + 2, hostLabelEndIndex};
    }

    @Override
    protected int[] fetchDatabaseNameIndexRange() {
        int databaseStartTag = url.lastIndexOf("/");
        int databaseEndTag = url.indexOf("?", databaseStartTag);
        if (databaseEndTag == -1) {
            databaseEndTag = url.length();
        }
        return new int[]{databaseStartTag + 1, databaseEndTag};
    }

    @Override
    public ConnectionInfo parse() {
        int[] hostRangeIndex = fetchDatabaseHostsIndexRange();
        String hosts = url.substring(hostRangeIndex[0], hostRangeIndex[1]);
        String[] hostSegment = hosts.split(",");
        if (hostSegment.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (String host : hostSegment) {
                if (host.split(":").length == 1) {
                    sb.append(host + ":" + DEFAULT_PORT + ",");
                } else {
                    sb.append(host + ",");
                }
            }
            return new ConnectionInfo(DB_TYPE, sb.toString(), fetchDatabaseNameFromURL());
        } else {
            String[] hostAndPort = hostSegment[0].split(":");
            if (hostAndPort.length != 1) {
                return new ConnectionInfo(DB_TYPE, hostAndPort[0], Integer.valueOf(hostAndPort[1]), fetchDatabaseNameFromURL());
            } else {
                return new ConnectionInfo(DB_TYPE, hostAndPort[0], DEFAULT_PORT, fetchDatabaseNameFromURL());
            }
        }
    }

}
