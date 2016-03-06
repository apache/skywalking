package com.ai.cloud.skywalking.analysis.chainbuild.util;

import com.ai.cloud.skywalking.analysis.chain2summary.entity.*;
import com.ai.cloud.skywalking.analysis.chainbuild.CallChainTree;
import com.ai.cloud.skywalking.analysis.chainbuild.po.CallChainTreeNode;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
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
import java.util.Map;

public class HBaseUtil {
    private static Logger logger = LoggerFactory.getLogger(HBaseUtil.class.getName());

    private static Configuration configuration = null;
    private static Connection connection;

    static {
        try {
            initHBaseClient();

//            createTableIfNeed(HBaseTableMetaData.TABLE_CID_TID_MAPPING.TABLE_NAME,
//                    HBaseTableMetaData.TABLE_CID_TID_MAPPING.COLUMN_FAMILY_NAME);
//
//            createTableIfNeed(HBaseTableMetaData.TABLE_CALL_CHAIN_RELATIONSHIP.TABLE_NAME,
//                    HBaseTableMetaData.TABLE_CALL_CHAIN_RELATIONSHIP.COLUMN_FAMILY_NAME);
//
//            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP.TABLE_NAME,
//                    HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP.COLUMN_FAMILY_NAME);
//
//            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_DETAIL.TABLE_NAME,
//                    HBaseTableMetaData.TABLE_CHAIN_DETAIL.COLUMN_FAMILY_NAME);
//
//            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_ONE_HOUR_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME,
//                    HBaseTableMetaData.TABLE_CHAIN_ONE_HOUR_SUMMARY_INCLUDE_RELATIONSHIP.COLUMN_FAMILY_NAME);
//
//            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME,
//                    HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY_INCLUDE_RELATIONSHIP.COLUMN_FAMILY_NAME);
//
//            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME,
//                    HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_INCLUDE_RELATIONSHIP.COLUMN_FAMILY_NAME);
//
//            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_ONE_MONTH_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME,
//                    HBaseTableMetaData.TABLE_CHAIN_ONE_MONTH_SUMMARY_INCLUDE_RELATIONSHIP.COLUMN_FAMILY_NAME);

            createTableIfNeed(HBaseTableMetaData.TABLE_MERGED_CHAIN_DETAIL.TABLE_NAME,
                    HBaseTableMetaData.TABLE_MERGED_CHAIN_DETAIL.COLUMN_FAMILY_NAME);

            createTableIfNeed(HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING.TABLE_NAME,
                    HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING.COLUMN_FAMILY_NAME);
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

    public static boolean saveCidTidMapping(String traceId, ChainInfo chainInfo) {
        Table table = null;

        try {
            table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CID_TID_MAPPING.TABLE_NAME));
        } catch (IOException e) {
            logger.error("Cannot found table[" + HBaseTableMetaData.TABLE_CID_TID_MAPPING.TABLE_NAME + "]", e);
        }

        Put put = new Put(Bytes.toBytes(traceId));

        put.addColumn(Bytes.toBytes(HBaseTableMetaData.TABLE_CID_TID_MAPPING.COLUMN_FAMILY_NAME),
                Bytes.toBytes(HBaseTableMetaData.TABLE_CID_TID_MAPPING.CID_COLUMN_NAME),
                Bytes.toBytes(chainInfo.getCID()));
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

    public static void saveChainRelationship(Put put) throws IOException {
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN_RELATIONSHIP.TABLE_NAME));

        table.put(put);
        if (logger.isDebugEnabled()) {
            logger.debug("Insert data[RowKey:{}] success.", put.getId());
        }
    }

    public static void batchSaveChainSpecificTimeWindowSummary(List<Put> puts) throws IOException, InterruptedException {
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP.TABLE_NAME));
        Object[] resultArrays = new Object[puts.size()];
        table.batch(puts, resultArrays);
        for (Object result : resultArrays) {
            if (result == null) {
                logger.error("Failed to save chain specificTimeWindows Summary.");
            }
        }
    }

    public static void saveChainDetails(List<Put> puts) throws IOException, InterruptedException {
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_DETAIL.TABLE_NAME));
        if (puts != null && puts.size() > 0) {
            Object[] resultArrays = new Object[puts.size()];
            table.batch(puts, resultArrays);
            for (Object result : resultArrays) {
                if (result == null) {
                    logger.error("Failed to save chain specificTimeWindows Summary.");
                }
            }
        }
    }

    public static ChainSpecificMinSummary loadSpecificMinSummary(String key) throws IOException {
        ChainSpecificMinSummary result = null;
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(key));
        Result r = table.get(g);

        if (r.rawCells().length == 0) {
            return null;
        }
        result = new ChainSpecificMinSummary();
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                result.addNodeSummaryResult(new ChainNodeSpecificMinSummary(Bytes.toString(cell.getValueArray(),
                        cell.getValueOffset(), cell.getValueLength())));
        }
        return result;
    }

    public static ChainSpecificHourSummary loadSpecificHourSummary(String key) throws IOException {
        ChainSpecificHourSummary result = null;
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_HOUR_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(key));
        Result r = table.get(g);

        if (r.rawCells().length == 0) {
            return null;
        }
        result = new ChainSpecificHourSummary();
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                result.addNodeSummaryResult(new ChainNodeSpecificHourSummary(Bytes.toString(cell.getValueArray(),
                        cell.getValueOffset(), cell.getValueLength())));
        }
        return result;
    }

    public static ChainSpecificDaySummary loadSpecificDaySummary(String key) throws IOException {
        ChainSpecificDaySummary result = null;
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(key));
        Result r = table.get(g);

        if (r.rawCells().length == 0) {
            return null;
        }
        result = new ChainSpecificDaySummary();
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                result.addNodeSummaryResult(new ChainNodeSpecificDaySummary(Bytes.toString(cell.getValueArray(),
                        cell.getValueOffset(), cell.getValueLength())));
        }
        return result;
    }

    public static ChainSpecificMonthSummary loadSpecificMonthSummary(String key) throws IOException {
        ChainSpecificMonthSummary result = null;
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_MONTH_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(key));
        Result r = table.get(g);

        if (r.rawCells().length == 0) {
            return null;
        }
        result = new ChainSpecificMonthSummary();
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                result.addNodeSummaryResult(new ChainNodeSpecificMonthSummary(Bytes.toString(cell.getValueArray(),
                        cell.getValueOffset(), cell.getValueLength())));
        }
        return result;
    }

    public static void batchSaveSpecificMinSummary(Map<String, ChainSpecificMinSummary> minSummary) throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainSpecificMinSummary> entry : minSummary.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            entry.getValue().save(put);
            puts.add(put);
        }

        batchSavePuts(puts, HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME);
    }

    public static void batchSaveSpecificDaySummary(Map<String, ChainSpecificDaySummary> daySummaryMap) throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainSpecificDaySummary> entry : daySummaryMap.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            entry.getValue().save(put);
            puts.add(put);
        }

        batchSavePuts(puts, HBaseTableMetaData.TABLE_CHAIN_ONE_DAY_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME);
    }

    public static void batchSaveSpecificHourSummary(Map<String, ChainSpecificHourSummary> hourSummaryMap) throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainSpecificHourSummary> entry : hourSummaryMap.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            entry.getValue().save(put);
            puts.add(put);
        }

        batchSavePuts(puts, HBaseTableMetaData.TABLE_CHAIN_ONE_HOUR_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME);
    }

    public static void batchSaveSpecificMonthSummary(Map<String, ChainSpecificMonthSummary> monthSummaryMap) throws IOException, InterruptedException {
        List<Put> puts = new ArrayList<Put>();
        for (Map.Entry<String, ChainSpecificMonthSummary> entry : monthSummaryMap.entrySet()) {
            Put put = new Put(entry.getKey().getBytes());
            entry.getValue().save(put);
            puts.add(put);
        }

        batchSavePuts(puts, HBaseTableMetaData.TABLE_CHAIN_ONE_MONTH_SUMMARY_INCLUDE_RELATIONSHIP.TABLE_NAME);
    }

    private static void batchSavePuts(List<Put> puts, String tableName) throws IOException, InterruptedException {
        Object[] resultArrays = new Object[puts.size()];
        Table table = connection.getTable(TableName.valueOf(tableName));
        table.batch(puts, resultArrays);
        for (Object result : resultArrays) {
            if (result == null) {
                logger.error("Failed to save chain specific Summary");
            }
        }
    }

    public static CallChainTree loadMergedCallChain(String callEntrance) throws IOException {
        CallChainTree result = null;
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_MERGED_CHAIN_DETAIL.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(callEntrance));
        Result r = table.get(g);
        if (r.rawCells().length == 0) {
            return null;
        }
        result = new CallChainTree(callEntrance);
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                result.addMergedChainNode(new CallChainTreeNode(Bytes.toString(cell.getValueArray(),
                        cell.getValueOffset(), cell.getValueLength())));
        }
        return result;
    }

    public static void saveCallChainTree(CallChainTree callChainTree) throws IOException {
        // save
        Put callChainTreePut = new Put(callChainTree.getCallEntrance().getBytes());
        for (Map.Entry<String, CallChainTreeNode> entry : callChainTree.getNodes().entrySet()) {
            callChainTreePut.addColumn(HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_DETAIL.COLUMN_FAMILY_NAME.getBytes(),
                    entry.getKey().getBytes(), entry.getValue().toString().getBytes());
        }
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_MERGED_CHAIN_DETAIL.TABLE_NAME));
        table.put(callChainTreePut);
        // save relationship
        Put treeIdCidMappingPut = new Put(callChainTree.getCallEntrance().getBytes());
        treeIdCidMappingPut.addColumn(HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING.COLUMN_FAMILY_NAME.getBytes()
                , "HAS_BEEN_MERGED_CHAIN_ID".getBytes(), callChainTree.getHasBeenMergedChainIds().getBytes());
        Table relationshipTable = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING.TABLE_NAME));
        relationshipTable.put(treeIdCidMappingPut);
    }

    public static List<String> loadHasBeenMergeChainIds(String treeId) throws IOException {
        List<String> result = new ArrayList<String>();
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(treeId));
        Result r = table.get(g);
        if (r.rawCells().length == 0) {
            return null;
        }
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0) {
                List<String> hasBeenMergedCIds = new Gson().fromJson("",
                        new TypeToken<List<String>>() {
                        }.getType());
                result.addAll(hasBeenMergedCIds);
            }

        }
        return result;
    }
}
