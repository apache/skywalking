package com.ai.cloud.skywalking.reciever.util;

import com.ai.cloud.skywalking.reciever.processor.exception.SaveToHBaseFailedException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;

import java.io.IOException;
import java.util.List;

public class HBaseUtil {

    public static void batchSavePuts(Connection connection, String tableName, List<Put> puts) {
        Object[] resultArrays = new Object[puts.size()];
        try {
            Table table = connection.getTable(TableName.valueOf(tableName));
            table.batch(puts, resultArrays);
            // ignore failed data
        } catch (IOException e) {
            throw new SaveToHBaseFailedException(e);
        } catch (InterruptedException e) {
            throw new SaveToHBaseFailedException(e);
        }
    }
}
