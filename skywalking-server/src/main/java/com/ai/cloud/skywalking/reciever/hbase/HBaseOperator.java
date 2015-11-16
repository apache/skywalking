package com.ai.cloud.skywalking.reciever.hbase;

import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.conf.ConfigInitializer;
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
import java.util.Properties;
import java.util.UUID;

public class HBaseOperator {
    private static Logger logger = LogManager.getLogger(HBaseOperator.class);
    private static Configuration configuration = null;
    private static Connection connection;

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

    private static void createTable(String tableName) {

        try {
            initHBaseClient();
            Admin admin = connection.getAdmin();
            if (!admin.isTableAvailable(TableName.valueOf(tableName))) {
                HTableDescriptor tableDesc = new HTableDescriptor(TableName.valueOf(tableName));
                tableDesc.addFamily(new HColumnDescriptor(Config.HBaseConfig.FAMILY_COLUMN_NAME));
                admin.createTable(tableDesc);
                logger.info("Create table [{}] ok!", tableName);
            }
        } catch (IOException e) {
            logger.error("Create table[{}] failed", tableName, e);
        }
    }

    public static void insert(String rowKey, String qualifier, String value) {
        insert(Config.HBaseConfig.TABLE_NAME, rowKey, qualifier, value);
    }

    public static void insert(String tableName, String rowKey, String qualifier, String value) {
        try {
            createTable(tableName);
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
        }
    }

    public static void main(String[] args) throws IllegalAccessException, IOException {
        Properties config = new Properties();
        config.load(HBaseOperator.class.getResourceAsStream("/config.properties"));
        ConfigInitializer.initialize(config, Config.class);
        HBaseOperator.createTable("test3");
    }
}
