package com.ai.cloud.skywalking.analysis.chainbuild.util;

import com.ai.cloud.skywalking.analysis.chainbuild.entity.ChainNodeSpecificDaySummary;
import com.ai.cloud.skywalking.analysis.chainbuild.entity.ChainNodeSpecificHourSummary;
import com.ai.cloud.skywalking.analysis.chainbuild.entity.ChainNodeSpecificMinSummary;
import com.ai.cloud.skywalking.analysis.chainbuild.entity.ChainNodeSpecificMonthSummary;
import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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

    static {
        try {
            initHBaseClient();

            createTableIfNeed(HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING.TABLE_NAME,
                    HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING.COLUMN_FAMILY_NAME);

            createTableIfNeed(HBaseTableMetaData.TABLE_TRACE_ID_AND_CID_MAPPING.TABLE_NAME,
                    HBaseTableMetaData.TABLE_TRACE_ID_AND_CID_MAPPING.COLUMN_FAMILY_NAME);

            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY.TABLE_NAME,
                    HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY.COLUMN_FAMILY_NAME);

            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_DETAIL.TABLE_NAME,
                    HBaseTableMetaData.TABLE_CHAIN_DETAIL.COLUMN_FAMILY_NAME);
            // 1 hour summary table
            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_ONE_HOUR_SUMMARY.TABLE_NAME,
                    HBaseTableMetaData.TABLE_CHAIN_ONE_HOUR_SUMMARY.COLUMN_FAMILY_NAME);
            // 1 day summary table
            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY.TABLE_NAME,
                    HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY.COLUMN_FAMILY_NAME);
            // 1 month summary table
            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_ONE_MONTH_SUMMARY.TABLE_NAME,
                    HBaseTableMetaData.TABLE_CHAIN_ONE_MONTH_SUMMARY.COLUMN_FAMILY_NAME);

        } catch (IOException e) {
            logger.error("Create tables failed", e);
        }
    }

    private static void createTableIfNeed(String tableName, String familyName) throws IOException {
        Admin admin = connection.getAdmin();
        if (!admin.isTableAvailable(TableName.valueOf(tableName))) {
            HTableDescriptor tableDesc = new HTableDescriptor(TableName.valueOf(tableName));
            tableDesc.addFamily(new HColumnDescriptor(familyName.getBytes()));
            admin.createTable(tableDesc);
            logger.info("Create table [{}] ok!", tableName);
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

    public static ChainNodeSpecificMinSummary loadSpecificMinSummary(String key, String qualifier) throws IOException {
        ChainNodeSpecificMinSummary result = null;
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(key));
        Result r = table.get(g);

        if (r.rawCells().length == 0) {
            return new ChainNodeSpecificMinSummary();
        }

        Cell cell = r.getColumnLatestCell(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY.COLUMN_FAMILY_NAME.getBytes(),
                qualifier.getBytes());

        if (cell != null && cell.getValueArray().length > 0) {
            result = new ChainNodeSpecificMinSummary(Bytes.toString(cell.getValueArray(),
                    cell.getValueOffset(), cell.getValueLength()));
        } else {
            result = new ChainNodeSpecificMinSummary();
        }

        return result;
    }

    public static ChainNodeSpecificHourSummary loadSpecificHourSummary(String keyOfHourSummaryTable, String treeNodeId) throws IOException {
        ChainNodeSpecificHourSummary result = null;
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_HOUR_SUMMARY.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(keyOfHourSummaryTable));
        Result r = table.get(g);

        if (r.rawCells().length == 0) {
            return new ChainNodeSpecificHourSummary();
        }

        Cell cell = r.getColumnLatestCell(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY.COLUMN_FAMILY_NAME.getBytes(),
                treeNodeId.getBytes());

        if (cell != null && cell.getValueArray().length > 0) {
            result = new ChainNodeSpecificHourSummary(Bytes.toString(cell.getValueArray(),
                    cell.getValueOffset(), cell.getValueLength()));
        } else {
            result = new ChainNodeSpecificHourSummary();
        }

        return result;
    }

    public static ChainNodeSpecificDaySummary loadSpecificDaySummary(String keyOfDaySummaryTable, String treeNodeId) throws IOException {
        ChainNodeSpecificDaySummary result = null;
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(keyOfDaySummaryTable));
        Result r = table.get(g);

        if (r.rawCells().length == 0) {
            return new ChainNodeSpecificDaySummary();
        }

        Cell cell = r.getColumnLatestCell(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY.COLUMN_FAMILY_NAME.getBytes(),
                treeNodeId.getBytes());

        if (cell != null && cell.getValueArray().length > 0) {
            result = new ChainNodeSpecificDaySummary(Bytes.toString(cell.getValueArray(),
                    cell.getValueOffset(), cell.getValueLength()));
        } else {
            result = new ChainNodeSpecificDaySummary();
        }

        return result;
    }

    public static ChainNodeSpecificMonthSummary loadSpecificMonthSummary(String keyOfMonthSummaryTable, String treeNodeId) throws IOException {
        ChainNodeSpecificMonthSummary result = null;
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(keyOfMonthSummaryTable));
        Result r = table.get(g);

        if (r.rawCells().length == 0) {
            return new ChainNodeSpecificMonthSummary();
        }

        Cell cell = r.getColumnLatestCell(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY.COLUMN_FAMILY_NAME.getBytes(),
                treeNodeId.getBytes());

        if (cell != null && cell.getValueArray().length > 0) {
            result = new ChainNodeSpecificMonthSummary(Bytes.toString(cell.getValueArray(),
                    cell.getValueOffset(), cell.getValueLength()));
        } else {
            result = new ChainNodeSpecificMonthSummary();
        }

        return result;
    }

    public static List<String> loadHasBeenMergeChainIds(String treeId) throws IOException {
        List<String> result = new ArrayList<String>();
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(treeId));
        Result r = table.get(g);
        if (r.rawCells().length == 0) {
            return new ArrayList<>();
        }
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0) {
                List<String> hasBeenMergedCIds = new Gson().fromJson(Bytes.toString(cell.getValueArray(),
                        cell.getValueOffset(), cell.getValueLength()),
                        new TypeToken<List<String>>() {
                        }.getType());
                result.addAll(hasBeenMergedCIds);
            }

        }
        return result;
    }

    public static void batchSaveMinSummaryResult(List<Put> puts) throws IOException, InterruptedException {
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY.TABLE_NAME));
        batchSaveData(puts, table);
    }

    public static void batchSaveMonthSummaryResult(List<Put> puts) throws IOException, InterruptedException {
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_MONTH_SUMMARY.TABLE_NAME));
        batchSaveData(puts, table);
    }

    public static void batchSaveDaySummaryResult(List<Put> puts) throws IOException, InterruptedException {
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY.TABLE_NAME));
        batchSaveData(puts, table);
    }

    public static void batchSaveHourSummaryResult(List<Put> puts) throws IOException, InterruptedException {
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_HOUR_SUMMARY.TABLE_NAME));
        batchSaveData(puts, table);
    }

    private static void batchSaveData(List<Put> puts, Table table) throws IOException, InterruptedException {
        Object[] resultArray = new Object[puts.size()];
        table.batch(puts, resultArray);
        int index = 0;
        for (Object result : resultArray) {
            if (result == null) {
                logger.error("Failed to insert the put the Value[" + puts.get(index).getId() + "]");
            }
            index++;
        }
    }

    public static void batchSaveChainInfo(List<Put> chainInfoPuts) throws IOException, InterruptedException {
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_DETAIL.TABLE_NAME));
        batchSaveData(chainInfoPuts, table);
    }

    public static void batchSaveHasBeenMergedCID(List<Put> chainIdPuts) throws IOException, InterruptedException {
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING.TABLE_NAME));
        batchSaveData(chainIdPuts, table);
    }


    public static void saveTraceIdAndTreeIdMapping(String traceId, String cid) throws IOException {
        Put put = new Put(traceId.getBytes());
        put.addColumn(HBaseTableMetaData.TABLE_TRACE_ID_AND_CID_MAPPING.COLUMN_FAMILY_NAME.getBytes(),
                HBaseTableMetaData.TABLE_TRACE_ID_AND_CID_MAPPING.COLUMN_NAME.getBytes(),
                cid.getBytes());
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_TRACE_ID_AND_CID_MAPPING.TABLE_NAME));
        table.put(put);
    }
}
