package org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql;

import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2HistoryDeleteDAO;
import org.joda.time.DateTime;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class TiDBHistoryDeleteDAO extends H2HistoryDeleteDAO {

    public TiDBHistoryDeleteDAO(JDBCHikariCPClient client) {
        super(client);
    }

    @Override
    public void deleteHistory(Model model, String timeBucketColumnName, int ttl) throws IOException {
        SQLBuilder dataDeleteSQL = new SQLBuilder("delete from " + model.getName() + " where ")
                .append(timeBucketColumnName).append("<= ? and ")
                .append(timeBucketColumnName).append(">= ?")
                .append(" limit 10000");
        long minTimeBucket = 0;
        DateTime minDate = new DateTime(1900, 1, 1, 0, 0);

        try (Connection connection = client.getConnection()) {
            long deadline;
            if (model.isRecord()) {
                deadline = Long.valueOf(new DateTime().plusDays(0 - ttl).toString("yyyyMMddHHmmss"));
            } else {
                switch (model.getDownsampling()) {
                    case Minute:
                        deadline = Long.valueOf(new DateTime().plusDays(0 - ttl).toString("yyyyMMddHHmm"));
                        minTimeBucket = Long.valueOf(minDate.toString("yyyyMMddHHmm"));
                        break;
                    case Hour:
                        deadline = Long.valueOf(new DateTime().plusDays(0 - ttl).toString("yyyyMMddHH"));
                        minTimeBucket = Long.valueOf(minDate.toString("yyyyMMddHH"));
                        break;
                    case Day:
                        deadline = Long.valueOf(new DateTime().plusDays(0 - ttl).toString("yyyyMMdd"));
                        minTimeBucket = Long.valueOf(minDate.toString("yyyyMMdd"));
                        break;
                    default:
                        return;
                }
            }
            while(client.executeUpdate(connection, dataDeleteSQL.toString(), deadline, minTimeBucket) > 0) {}
        } catch (JDBCClientException | SQLException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
