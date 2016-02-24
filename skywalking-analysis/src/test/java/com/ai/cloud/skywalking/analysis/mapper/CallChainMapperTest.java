package com.ai.cloud.skywalking.analysis.mapper;


import com.ai.cloud.skywalking.analysis.categorize2chain.Categorize2ChainMapper;
import com.ai.cloud.skywalking.analysis.categorize2chain.Categorize2ChainReducer;
import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.chain2summary.Chain2SummaryReducer;
import com.ai.cloud.skywalking.analysis.chain2summary.ChainSpecificTimeSummary;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.ai.cloud.skywalking.protocol.Span;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by astraea on 2016/1/15.
 */
public class CallChainMapperTest {

    private static String ZK_QUORUM = "10.1.235.197,10.1.235.198,10.1.235.199";
    private static String ZK_CLIENT_PORT = "29181";
//     private static String chain_Id = "1.0a2.1453430186581.3efa259.4296.56.1";
    private static String chain_Id = "1.0a2.1453429608422.2701d43.6468.56.1";
    private static String[] summaryRowKeys = {"CID_FF44EB45B69FCC1BE1C09F25DB1DFCEF-5-2016/01/26 20:00:00","CID_EE53EE49E36184A9901A7E5C4993C4F2-5-2016/01/27 14:00:00","CID_53AAB989D315C98CBA7352474EEFA57F-5-2016/01/30 10:00:00"};

    private static Configuration configuration = null;
    private static Connection connection;

    @Test
    public void testMap() throws Exception {
        ConfigInitializer.initialize();
        List<Span> spanList = selectByTraceId(chain_Id);
        ChainInfo chainInfo = Categorize2ChainMapper.spanToChainInfo(chain_Id, spanList);

        List<ChainInfo> chainInfos = new ArrayList<ChainInfo>();
        chainInfos.add(chainInfo);

        Categorize2ChainReducer.reduceAction(chainInfo.getUserId() + ":" + chainInfo.getEntranceNodeToken(), chainInfos.iterator());
    }

    public static List<Span> selectByTraceId(String traceId) throws IOException {
        List<Span> entries = new ArrayList<Span>();
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(traceId));
        Result r = table.get(g);
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                entries.add(new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())));
        }
        return entries;
    }


    @Before
    public void initHBaseClient() throws IOException {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();
            configuration.set("hbase.zookeeper.quorum", ZK_QUORUM);
            configuration.set("hbase.zookeeper.property.clientPort", ZK_CLIENT_PORT);
            connection = ConnectionFactory.createConnection(configuration);
        }
    }

    @Test
    public void testSummary() throws IOException, ParseException {
        ConfigInitializer.initialize();

        Map<String,List<ChainSpecificTimeSummary>> chainSpecificTimeSummaries = new HashMap();
        for (String rowkey : summaryRowKeys) {
            ChainSpecificTimeSummary chainSpecificDaySummary = selectSummary(rowkey);
            List<ChainSpecificTimeSummary> chainSpecificTimeSummaries1 = chainSpecificTimeSummaries.get(chainSpecificDaySummary.buildMapperKey());
            if (chainSpecificTimeSummaries1 == null){
                chainSpecificTimeSummaries1 = new ArrayList<>();
            }
            chainSpecificTimeSummaries1.add(chainSpecificDaySummary);
            chainSpecificTimeSummaries.put(chainSpecificDaySummary.buildMapperKey(), chainSpecificTimeSummaries1);
        }

        Chain2SummaryReducer reducer = new Chain2SummaryReducer();
        for (Map.Entry<String, List<ChainSpecificTimeSummary>> entry : chainSpecificTimeSummaries.entrySet()) {
            reducer.doReduceAction(entry.getKey(), entry.getValue().iterator());
        }

        System.out.println("1");
    }

    private ChainSpecificTimeSummary selectSummary(String key) throws IOException, ParseException {
        ChainSpecificTimeSummary summary = new ChainSpecificTimeSummary(Bytes.toString(key.getBytes()));

        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(key));
        Result value = table.get(g);
        for (Cell cell : value.rawCells()) {
            summary.addChainNodeSummaryResult(Bytes.toString(cell.getValueArray(),
                    cell.getValueOffset(), cell.getValueLength()));
        }

        return summary;
    }
}