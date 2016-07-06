package com.ai.cloud.skywalking.reciever.util;

import com.ai.cloud.skywalking.reciever.processor.ProcessorFactory;
import com.ai.cloud.skywalking.reciever.processor.exception.SaveToHBaseFailedException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

public class HBaseUtil {
    private static Logger logger = LogManager.getLogger(HBaseUtil.class);

    public static void batchSavePuts(Connection connection, String tableName, List<Put> puts) {
        Object[] resultArrays = new Object[puts.size()];
        try {
            Table table = connection.getTable(TableName.valueOf(tableName));
            table.batch(puts, resultArrays);
            // ignore failed data
        } catch (IOException e) {
            logger.error("batchSavePuts failure.", e);
        } catch (InterruptedException e) {
            logger.error("batchSavePuts failure.", e);
        }
    }
}
