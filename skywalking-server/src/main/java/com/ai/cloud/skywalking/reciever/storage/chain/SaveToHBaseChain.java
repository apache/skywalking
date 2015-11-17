package com.ai.cloud.skywalking.reciever.storage.chain;

import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.model.BuriedPointEntry;
import com.ai.cloud.skywalking.reciever.storage.Chain;
import com.ai.cloud.skywalking.reciever.storage.ChainException;
import com.ai.cloud.skywalking.reciever.storage.IStorageChain;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class SaveToHBaseChain implements IStorageChain {
    private static Logger logger = LogManager.getLogger(SaveToHBaseChain.class);
    private static Configuration configuration = null;
    private static Connection connection;

    @Override
    public void doChain(BuriedPointEntry entry, String entryOriginData, Chain chain) {
        if (StringUtils.isEmpty(entry.getParentLevel().trim())) {
            insert(entry.getTraceId(), String.valueOf(entry.getLevelId()), entryOriginData);
        } else {
            insert(entry.getTraceId(), entry.getParentLevel() + "." + entry.getLevelId(), entryOriginData);
        }

        chain.doChain(entry, entryOriginData);
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

    public static void insert(String rowKey, String qualifier, String value) {
        insert(Config.HBaseConfig.TABLE_NAME, rowKey, qualifier, value);
    }

    public static void insert(String tableName, String rowKey, String qualifier, String value) {
        try {
            Table table = connection.getTable(TableName.valueOf(tableName));
            Put put = new Put(Bytes.toBytes(rowKey));
            put.addColumn(Bytes.toBytes(Config.HBaseConfig.FAMILY_COLUMN_NAME), Bytes.toBytes(qualifier), Bytes
                    .toBytes(value));
            table.put(put);
            if (logger.isDebugEnabled()) {
                logger.debug("Insert data[RowKey:{}] success.", rowKey);
            }
        } catch (IOException e) {
            logger.error("Insert the data error.RowKey:[{}],Qualifier[{}],value[{}]", rowKey, qualifier, value, e);
            throw new ChainException(e);
        }
    }

}
