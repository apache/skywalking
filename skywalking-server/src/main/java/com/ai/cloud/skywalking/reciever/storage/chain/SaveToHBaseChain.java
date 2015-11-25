package com.ai.cloud.skywalking.reciever.storage.chain;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;
import com.ai.cloud.skywalking.reciever.storage.Chain;
import com.ai.cloud.skywalking.reciever.storage.ChainException;
import com.ai.cloud.skywalking.reciever.storage.IStorageChain;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SaveToHBaseChain implements IStorageChain {
    private static Logger logger = LogManager.getLogger(SaveToHBaseChain.class);
    private static Configuration configuration = null;
    private static Connection connection;

    @Override
    public void doChain(List<Span> spans, Chain chain) {
        bulkInsertBuriedPointData(spans);
        chain.doChain(spans);
    }

    private static void initHBaseClient() throws IOException {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();
            if (Config.HBaseConfig.ZK_HOSTNAME == null || "".equals(Config.HBaseConfig.ZK_HOSTNAME)) {
                logger.error("Miss HBase ZK quorum Configuration", new IllegalArgumentException("Miss HBase ZK quorum Configuration"));
                System.exit(-1);
            }
            configuration.set("hbase.zookeeper.quorum", Config.HBaseConfig.ZK_HOSTNAME);
            configuration.set("hbase.zookeeper.property.clientPort", Config.HBaseConfig.CLIENT_PORT);
            connection = ConnectionFactory.createConnection(configuration);
        }
    }

    static {
        try {
            initHBaseClient();
            Admin admin = connection.getAdmin();
            if (!admin.isTableAvailable(TableName.valueOf(Config.HBaseConfig.TABLE_NAME))) {
                HTableDescriptor tableDesc = new HTableDescriptor(TableName.valueOf(Config.HBaseConfig.TABLE_NAME));
                tableDesc.addFamily(new HColumnDescriptor(Config.HBaseConfig.FAMILY_COLUMN_NAME));
                admin.createTable(tableDesc);
                logger.info("Create table [{}] ok!", Config.HBaseConfig.TABLE_NAME);
            }
        } catch (IOException e) {
            logger.error("Create table[{}] failed", Config.HBaseConfig.TABLE_NAME, e);
        }
    }

    public static boolean insert(String tableName, Put put) {
        try {
            Table table = connection.getTable(TableName.valueOf(tableName));
            table.put(put);
            if (logger.isDebugEnabled()) {
                logger.debug("Insert data[RowKey:{}] success.", put.getId());
            }
            return true;
        } catch (IOException e) {
            logger.error("Insert the data error.RowKey:[{}]", put.getId(), e);
            return false;
        }

    }

    private static void bulkInsertBuriedPointData(List<Span> spans) {
        if (spans == null || spans.size() <= 0)
            return;
        List<Put> puts = new ArrayList<Put>();
        Put put;
        String columnName;
        for (Span span : spans) {
            put = new Put(Bytes.toBytes(span.getTraceId()));
            if (StringUtils.isEmpty(span.getParentLevel().trim())) {
                columnName = span.getLevelId() + "";
                if (span.isReceiver()) {
                    columnName = span.getLevelId() + "-S";
                }
                put.addColumn(Bytes.toBytes(Config.HBaseConfig.FAMILY_COLUMN_NAME), Bytes.toBytes(columnName),
                        Bytes.toBytes(span.getOriginData()));
            } else {
                columnName = span.getParentLevel() + "." + span.getLevelId();
                if (span.isReceiver()) {
                    columnName = span.getParentLevel() + "." + span.getLevelId() + "-S";
                }
                put.addColumn(Bytes.toBytes(Config.HBaseConfig.FAMILY_COLUMN_NAME), Bytes.toBytes(columnName),
                        Bytes.toBytes(span.getOriginData()));
            }
            puts.add(put);
        }

        bulkInsertBuriedPointData(Config.HBaseConfig.TABLE_NAME, puts);

        ServerHealthCollector.getCurrentHeathReading("hbase").updateData(ServerHeathReading.INFO, "save " + spans.size() + " BuriedPointEntries.");
    }

    private static void bulkInsertBuriedPointData(String tableName, List<Put> data) {
        Object[] resultArrays = new Object[data.size()];
        try {
            Table table = connection.getTable(TableName.valueOf(tableName));
            table.batch(data, resultArrays);
            int index = 0;
            for (Object result : resultArrays) {
                if (result == null) {
                    while (!insert(tableName, data.get(index))) {
                        Thread.sleep(100L);
                    }
                }
                index++;
            }
        } catch (IOException e) {
            throw new ChainException(e);
        } catch (InterruptedException e) {
            throw new ChainException(e);
        }

    }

    public static List<Span> selectByTraceId(String traceId) throws IOException {
        List<Span> entries = new ArrayList<Span>();
        Table table = connection.getTable(TableName.valueOf(Config.HBaseConfig.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(traceId));
        Result r = table.get(g);
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                entries.add(new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())));
        }
        return entries;
    }
}
