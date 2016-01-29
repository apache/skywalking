package com.ai.cloud.skywalking.analysis.util;

import com.ai.cloud.skywalking.analysis.categorize2chain.*;
import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.config.Constants;
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

    public static boolean saveData(String traceId, ChainInfo chainInfo) {
        Table table = null;

        try {
            table = connection.getTable(TableName.valueOf(Constants.TABLE_CID_TID_MAPPING));
        } catch (IOException e) {
            logger.error("Cannot found table[" + Constants.TABLE_CID_TID_MAPPING + "]", e);
        }

        Put put = new Put(Bytes.toBytes(traceId));

        put.addColumn(Bytes.toBytes(Constants.COLUMN_FAMILY_NAME_TRACE_INFO),
                Bytes.toBytes(Constants.COLUMN_FAMILY_NAME_CID),
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

    static {
        try {
            initHBaseClient();
            //
            createTableIfNeed(Constants.TABLE_CID_TID_MAPPING, Constants.COLUMN_FAMILY_NAME_TRACE_INFO);
            //
            createTableIfNeed(Constants.TABLE_CALL_CHAIN_RELATIONSHIP, Constants.COLUMN_FAMILY_CHAIN_RELATIONSHIP);

            createTableIfNeed(Constants.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP, Constants.COLUMN_FAMILY_NAME_CHAIN_SUMMARY);

            createTableIfNeed(Constants.TABLE_CHAIN_DETAIL, Constants.COLUMN_FAMILY_NAME_TRACE_DETAIL);

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


    public static ChainRelationship selectCallChainRelationship(String key) throws IOException {
        ChainRelationship chainRelate = new ChainRelationship(key);
        Table table = connection.getTable(TableName.valueOf(Constants.TABLE_CALL_CHAIN_RELATIONSHIP));
        Get g = new Get(Bytes.toBytes(key));
        Result r = table.get(g);
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0) {

                String qualifierName = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(),
                        cell.getQualifierLength());
                if (Constants.UNCATEGORIZE_COLUMN_FAMILY.equals(qualifierName)) {
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
        Table table = connection.getTable(TableName.valueOf(Constants.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP));
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
        Table table = connection.getTable(TableName.valueOf(Constants.TABLE_CALL_CHAIN_RELATIONSHIP));

        table.put(put);
        if (logger.isDebugEnabled()) {
            logger.debug("Insert data[RowKey:{}] success.", put.getId());
        }
    }

    public static void batchSaveChainSpecificTimeWindowSummary(List<Put> puts) throws IOException, InterruptedException {
        Table table = connection.getTable(TableName.valueOf(Constants.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP));
        Object[] resultArrays = new Object[puts.size()];
        table.batch(puts, resultArrays);
        for (Object result : resultArrays) {
            if (result == null) {
                logger.error("Failed to save chain specificTimeWindows Summary.");
            }
        }
    }

    public static void saveChainDetails(List<Put> puts) throws IOException, InterruptedException {
        Table table = connection.getTable(TableName.valueOf(Constants.TABLE_CHAIN_DETAIL));
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
