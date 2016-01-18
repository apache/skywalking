package com.ai.cloud.skywalking.analysis.mapper;

import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessChain;
import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.analysis.model.SpanEntry;
import com.ai.cloud.skywalking.analysis.util.HBaseUtil;
import com.ai.cloud.skywalking.analysis.util.TokenGenerator;
import com.ai.cloud.skywalking.protocol.Span;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallChainMapper extends TableMapper<Text, ChainInfo> {
    private Logger logger = LoggerFactory.getLogger(CallChainMapper.class.getName());

    @Override
    protected void map(ImmutableBytesWritable key, Result value, Context context) throws IOException,
            InterruptedException {

        ChainInfo chainInfo = new ChainInfo();
        List<Span> spanList = new ArrayList<Span>();
        CostMap costMap = new CostMap();
        for (Cell cell : value.rawCells()) {
            Span span = new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()));
            spanList.add(span);
        }

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
        HBaseUtil.saveData(key.toString(), chainInfo);

        context.write(new Text(key.toString() + ":" + firstNodeToken), chainInfo);
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
            if (spanEntry == null) {
                spanEntry = new SpanEntry();
                spanEntryMap.put(span.getParentLevel() + "." + span.getLevelId(), spanEntry);
            }
            spanEntry.setSpan(span);
        }
        return spanEntryMap;
    }
}
