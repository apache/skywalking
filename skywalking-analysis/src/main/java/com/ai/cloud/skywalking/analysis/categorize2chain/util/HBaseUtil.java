package com.ai.cloud.skywalking.analysis.categorize2chain.util;

import com.ai.cloud.skywalking.analysis.categorize2chain.*;
import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainInfo;
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
import java.util.List;

public class HBaseUtil {
    private static Logger logger = LoggerFactory.getLogger(HBaseUtil.class.getName());

    private static Configuration configuration = null;
    private static Connection connection;

    static {
        try {
            initHBaseClient();

            createTableIfNeed(HBaseTableMetaData.TABLE_CID_TID_MAPPING.TABLE_NAME, HBaseTableMetaData.TABLE_CID_TID_MAPPING.COLUMN_FAMILY_NAME);

            createTableIfNeed(HBaseTableMetaData.TABLE_CALL_CHAIN_RELATIONSHIP.TABLE_NAME, HBaseTableMetaData.TABLE_CALL_CHAIN_RELATIONSHIP.COLUMN_FAMILY_NAME);

            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP.TABLE_NAME, HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP.COLUMN_FAMILY_NAME);

            createTableIfNeed(HBaseTableMetaData.TABLE_CHAIN_DETAIL.TABLE_NAME, HBaseTableMetaData.TABLE_CHAIN_DETAIL.COLUMN_FAMILY_NAME);

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


    public static ChainRelationship selectCallChainRelationship(String key) throws IOException {
        ChainRelationship chainRelate = new ChainRelationship(key);
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN_RELATIONSHIP.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(key));
        Result r = table.get(g);
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0) {

                String qualifierName = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(),
                        cell.getQualifierLength());
                if (HBaseTableMetaData.TABLE_CALL_CHAIN_RELATIONSHIP.UNCATEGORIZE_COLUMN_NAME.equals(qualifierName)) {
                    List<UncategorizeChainInfo> uncategorizeChainInfoList = new Gson().fromJson(Bytes.toString(cell.getValueArray(),
                            cell.getValueOffset(), cell.getValueLength()),
                            new TypeToken<List<UncategorizeChainInfo>>() {
                            }.getType());
                    chainRelate.addUncategorizeChain(uncategorizeChainInfoList);
                } else {
                    chainRelate.addCategorizeChain(qualifierName, new CategorizedChainInfo(
                            Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())
                    ));
                }
            }
        }
        return chainRelate;
    }

    public static ChainSpecificTimeWindowSummary selectChainSummaryResult(String key) throws IOException {
        ChainSpecificTimeWindowSummary result = null;
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(key));
        Result r = table.get(g);

        if (r.rawCells().length == 0) {
            return null;
        }
        result = new ChainSpecificTimeWindowSummary();
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                result.addNodeSummaryResult(new ChainNodeSpecificTimeWindowSummary(Bytes.toString(cell.getValueArray(),
                        cell.getValueOffset(), cell.getValueLength())));
        }

        return result;
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
}
