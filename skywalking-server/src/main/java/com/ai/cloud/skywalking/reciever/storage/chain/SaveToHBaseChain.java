package com.ai.cloud.skywalking.reciever.storage.chain;

import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.model.BuriedPointEntry;
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
    public void doChain(List<BuriedPointEntry> entry, Chain chain) {
        bulkInsertBuriedPointData(entry);
        chain.doChain(entry);
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

    private static void bulkInsertBuriedPointData(List<BuriedPointEntry> entries) {
        if (entries == null || entries.size() <= 0)
            return;
        List<Put> puts = new ArrayList<Put>();
        Put put;
        String columnName;
        for (BuriedPointEntry buriedPointEntry : entries) {
            put = new Put(Bytes.toBytes(buriedPointEntry.getTraceId()));
            if (StringUtils.isEmpty(buriedPointEntry.getParentLevel().trim())) {
                columnName = buriedPointEntry.getLevelId() + "";
                if (buriedPointEntry.isReceiver()) {
                    columnName = buriedPointEntry.getLevelId() + "-S";
                }
                put.addColumn(Bytes.toBytes(Config.HBaseConfig.FAMILY_COLUMN_NAME), Bytes.toBytes(columnName),
                        Bytes.toBytes(buriedPointEntry.getOriginData()));
            } else {
                columnName = buriedPointEntry.getParentLevel() + "." + buriedPointEntry.getLevelId();
                if (buriedPointEntry.isReceiver()) {
                    columnName = buriedPointEntry.getParentLevel() + "." + buriedPointEntry.getLevelId() + "-S";
                }
                put.addColumn(Bytes.toBytes(Config.HBaseConfig.FAMILY_COLUMN_NAME), Bytes.toBytes(columnName),
                        Bytes.toBytes(buriedPointEntry.getOriginData()));
            }
            puts.add(put);
        }

        bulkInsertBuriedPointData(Config.HBaseConfig.TABLE_NAME, puts);
        
        ServerHealthCollector.getCurrentHeathReading("hbase").updateData(ServerHeathReading.INFO, "save " + entries.size() + " BuriedPointEntries." );
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

    public static List<BuriedPointEntry> selectByTraceId(String traceId) throws IOException {
        List<BuriedPointEntry> entries = new ArrayList<BuriedPointEntry>();
        Table table = connection.getTable(TableName.valueOf(Config.HBaseConfig.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(traceId));
        Result r = table.get(g);
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                entries.add(BuriedPointEntry.convert(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())));
        }
        return entries;
    }
}
