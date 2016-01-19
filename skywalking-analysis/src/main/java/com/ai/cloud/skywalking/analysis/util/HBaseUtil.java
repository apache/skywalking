package com.ai.cloud.skywalking.analysis.util;

import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.protocol.Span;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HBaseUtil {
    private static Logger logger = LoggerFactory.getLogger(HBaseUtil.class.getName());

    private static Configuration configuration = null;
    private static Connection connection;

    public static boolean saveData(String traceId, ChainInfo chainInfo) {
        Table table = null;

        try {
            table = connection.getTable(TableName.valueOf(Config.HBase.TRACE_INFO_TABLE_NAME));
        } catch (IOException e) {
            logger.error("Cannot found table[" + Config.HBase.TRACE_INFO_TABLE_NAME + "]", e);
        }

        Put put = new Put(Bytes.toBytes(traceId));

        put.addColumn(Bytes.toBytes(Config.HBase.TRACE_INFO_COLUMN_FAMILY),
                Bytes.toBytes(Config.TraceInfo.TRACE_INFO_COLUMN_CID),
                Bytes.toBytes(chainInfo.getChainToken()));
        try {
            table.put(put);
            if (logger.isDebugEnabled()) {
                logger.debug("Insert data[RowKey:{}] success.", put.getId());
            }
        } catch (IOException e) {
            logger.error("Insert data [Rowkey:{}] failed.", put.getId(), e);
            return false;
        }

        return true;
    }

    static {
        try {
            initHBaseClient();
            Admin admin = connection.getAdmin();
            if (!admin.isTableAvailable(TableName.valueOf(Config.HBase.TRACE_INFO_TABLE_NAME))) {
                HTableDescriptor tableDesc = new HTableDescriptor(TableName.valueOf(Config.HBase.TRACE_INFO_TABLE_NAME));
                tableDesc.addFamily(new HColumnDescriptor(Config.HBase.TRACE_INFO_COLUMN_FAMILY));
                admin.createTable(tableDesc);
                logger.info("Create table [{}] ok!", Config.HBase.TRACE_INFO_TABLE_NAME);
            }
        } catch (IOException e) {
            logger.error("Create table[{}] failed", Config.HBase.TRACE_INFO_TABLE_NAME, e);
        }
    }

    private static void initHBaseClient() throws IOException {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();
            if (Config.HBase.ZK_QUORUM == null || "".equals(Config.HBase.ZK_QUORUM)) {
                logger.error("Miss HBase ZK quorum Configuration", new IllegalArgumentException("Miss HBase ZK quorum Configuration"));
                System.exit(-1);
            }
            configuration.set("hbase.zookeeper.quorum", Config.HBase.ZK_QUORUM);
            configuration.set("hbase.zookeeper.property.clientPort", Config.HBase.ZK_CLIENT_PORT);
            connection = ConnectionFactory.createConnection(configuration);
        }
    }


    public static void selectById(String id) throws IOException {
        List<Span> entries = new ArrayList<Span>();
        Table table = connection.getTable(TableName.valueOf(Config.HBase.TABLE_CALL_CHAIN_RELATIONSHIP));
        Get g = new Get(Bytes.toBytes(id));
        Result r = table.get(g);
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                entries.add(new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())));
        }
    }

}
