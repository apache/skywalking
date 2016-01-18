package com.ai.cloud.skywalking.analysis.mapper;


import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessChain;
import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.analysis.model.SpanEntry;
import com.ai.cloud.skywalking.analysis.util.HBaseUtil;
import com.ai.cloud.skywalking.analysis.util.TokenGenerator;
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
    private static String chain_Id = "1.0a2.1452852040127.0664234.11036.55.1";

    private static Configuration configuration = null;
    private static Connection connection;

    @Test
    public void testMap() throws Exception {
        ConfigInitializer.initialize();
        List<Span> spanList = selectByTraceId(chain_Id);
        ChainInfo chainInfo = new ChainInfo();
        CostMap costMap = new CostMap();

        Map<String, SpanEntry> spanEntryMap = mergeSpanDataset(spanList);
        for (Map.Entry<String, SpanEntry> entry : spanEntryMap.entrySet()) {
            ChainNode chainNode = new ChainNode();
            SpanNodeProcessFilter filter = SpanNodeProcessChain.getProcessChainByCallType(entry.getValue().getSpanType());
            filter.doFilter(entry.getValue(), chainNode, costMap);
            chainInfo.getNodes().add(chainNode);
        }

        //
        String firstNodeToken = null;
        boolean status = true;
        StringBuilder stringBuilder = new StringBuilder();
        for (ChainNode node : chainInfo.getNodes()) {
            if ((node.getParentLevelId() == null || node.getParentLevelId().length() == 0)
                    && node.getLevelId() == 0) {
                firstNodeToken = node.getNodeToken();
                chainInfo.setUserId(node.getUserId());
            }

            // 状态轮询
            if (node.getStatus() == ChainNode.NodeStatus.ABNORMAL) {
                status = false;
            }

            // 设置时间
            computeChainNodeCost(costMap, node);

            stringBuilder.append(node.getParentLevelId() + "." + node.getLevelId() + "-" + node.getNodeToken() + ";");
        }

        // 设置状态
        if (status) {
            chainInfo.setChainStatus(ChainInfo.ChainStatus.NORMAL);
        } else {
            chainInfo.setChainStatus(ChainInfo.ChainStatus.ABNORMAL);
        }

        // 设置Token
        chainInfo.setChainToken(TokenGenerator.generate(stringBuilder.toString()));

        //SaveToHbase
        HBaseUtil.saveData(chain_Id, chainInfo);

        System.out.println(chainInfo);
    }

    private void computeChainNodeCost(CostMap costMap, ChainNode node) {
        String levelId = node.getParentLevelId();
        if (levelId != null && levelId.length() > 0) {
            levelId += ".";
        }
        levelId += node.getLevelId() + "";

        if (costMap.get(levelId) != null) {
            node.setCost(node.getCost() - costMap.get(levelId));
        }
    }

    private Map<String, SpanEntry> mergeSpanDataset(List<Span> spanList) {
        Map<String, SpanEntry> spanEntryMap = new HashMap<String, SpanEntry>();
        for (Span span : spanList) {
            SpanEntry spanEntry = spanEntryMap.get(span.getParentLevel() + "." + span.getLevelId());
            if (spanEntry != null && span.isReceiver()) {
                if (span.isReceiver()) {
                    spanEntry.setServerSpan(span);
                } else {
                    spanEntry.setClientSpan(span);
                }
            } else {
                spanEntry = new SpanEntry();
                if (span.isReceiver()) {
                    spanEntry.setServerSpan(span);
                } else {
                    spanEntry.setClientSpan(span);
                }
                spanEntryMap.put(span.getParentLevel() + "." + span.getLevelId(), spanEntry);
            }
        }
        return spanEntryMap;
    }

    public static List<Span> selectByTraceId(String traceId) throws IOException {
        List<Span> entries = new ArrayList<Span>();
        Table table = connection.getTable(TableName.valueOf(Config.HBase.CALL_CHAIN_TABLE_NAME));
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
}