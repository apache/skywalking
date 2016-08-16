package com.a.eye.skywalking.reciever.util;

import com.a.eye.skywalking.reciever.conf.Config;
import com.a.eye.skywalking.reciever.processor.exception.HBaseInitFailedException;
import com.a.eye.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.a.eye.skywalking.reciever.selfexamination.ServerHeathReading;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

import static org.apache.hadoop.hbase.util.Bytes.toBytes;

public class HBaseUtil {
    private static Logger logger = LogManager.getLogger(HBaseUtil.class);

    public static void batchSavePuts(Connection connection, String tableName, List<Put> puts) {
        Object[] resultArrays = new Object[puts.size()];
        try {
            Table table = connection.getTable(TableName.valueOf(tableName));
            table.batch(puts, resultArrays);
        } catch (IOException e) {
            logger.error("batchSavePuts failure.", e);
        } catch (InterruptedException e) {
            logger.error("batchSavePuts failure.", e);
        }
    }

    private static Connection connection;

    public static Connection initConnection() {
        Configuration configuration = HBaseConfiguration.create();
        if (Config.HBaseConfig.ZK_HOSTNAME == null || "".equals(Config.HBaseConfig.ZK_HOSTNAME)) {
            logger.error("Miss HBase ZK quorum Configuration",
                    new IllegalArgumentException("Miss HBase ZK quorum Configuration"));
            System.exit(-1);
        }
        configuration.set("hbase.zookeeper.quorum", Config.HBaseConfig.ZK_HOSTNAME);
        configuration.set("hbase.zookeeper.property.clientPort", Config.HBaseConfig.CLIENT_PORT);

        try {
            Connection connection = ConnectionFactory.createConnection(configuration);
            Admin admin = connection.getAdmin();
            if (!admin.tableExists(TableName.valueOf(Config.HBaseConfig.TraceDataTable.TABLE_NAME))) {
                HTableDescriptor descriptor = new HTableDescriptor(TableName.valueOf(Config.HBaseConfig.TraceDataTable.TABLE_NAME));
                HColumnDescriptor family = new HColumnDescriptor(toBytes(Config.HBaseConfig.TraceDataTable.FAMILY_COLUMN_NAME));
                descriptor.addFamily(family);
                admin.createTable(descriptor);
            }

            if (!admin.tableExists(TableName.valueOf(Config.HBaseConfig.TraceParamTable.TABLE_NAME))) {
                HTableDescriptor descriptor = new HTableDescriptor(TableName.valueOf(Config.HBaseConfig.TraceParamTable.TABLE_NAME));
                HColumnDescriptor family = new HColumnDescriptor(toBytes(Config.HBaseConfig.TraceParamTable.FAMILY_COLUMN_NAME));
                descriptor.addFamily(family);
                admin.createTable(descriptor);
            }

            return connection;
        } catch (IOException e) {
            ServerHealthCollector.getCurrentHeathReading("hbase")
                    .updateData(ServerHeathReading.ERROR, "connect to hbase failure.");
            throw new HBaseInitFailedException("initHBaseClient failure", e);
        }
    }

    public static Connection getConnection() {
        if (connection == null) {
            connection = initConnection();
        }

        return connection;
    }
}
