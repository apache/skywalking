package com.ai.cloud.skywalking.analysis.mapper.util;

import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.ai.cloud.skywalking.protocol.Span;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 16-5-4.
 */
public class HBaseUtils {

    private static String ZK_QUORUM = "host-10-1-241-18,host-10-1-241-19,host-10-1-241-20";
    private static String ZK_CLIENT_PORT = "29181";

    private static Configuration configuration = null;
    private static Connection connection;

    static {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();
            configuration.set("hbase.zookeeper.quorum", ZK_QUORUM);
            configuration.set("hbase.zookeeper.property.clientPort", ZK_CLIENT_PORT);
            configuration.set("hbase.rpc.timeout", "600000");
            try {
                connection = ConnectionFactory.createConnection(configuration);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }

        ConfigInitializer.initialize();
    }

    public static Connection getConnection() {
        return connection;
    }

    public static List<Span> selectByTraceId(String traceId) throws IOException {
        List<Span> entries = new ArrayList<Span>();
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(traceId));
        Result r = table.get(g);
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                entries.add(new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())));
        }
        return entries;
    }
}
