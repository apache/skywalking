package com.ai.cloud.skywalking.analysis.mapper;

import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessChain;
import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.util.HBaseUtil;
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
import java.util.*;

public class CallChainMapper extends TableMapper<Text, ChainInfo> {
    private Logger logger = LoggerFactory.getLogger(CallChainMapper.class.getName());

    @Override
    protected void map(ImmutableBytesWritable key, Result value, Context context) throws IOException,
            InterruptedException {
        List<Span> spanList = new ArrayList<Span>();
        for (Cell cell : value.rawCells()) {
            Span span = new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()));
            spanList.add(span);
        }
        ChainInfo chainInfo = spanToChainInfo(key.toString(), spanList);
        context.write(new Text(chainInfo.getUserId() + ":" + chainInfo.getEntranceNodeToken()), chainInfo);
    }

    public static ChainInfo spanToChainInfo(String key, List<Span> spanList) {
        CostMap costMap = new CostMap();
        ChainInfo chainInfo = new ChainInfo();
        Collections.sort(spanList, new Comparator<Span>() {
            @Override
            public int compare(Span span1, Span span2) {
                String span1TraceLevel = span1.getParentLevel() + "." + span1.getLevelId();
                String span2TraceLevel = span2.getParentLevel() + "." + span2.getLevelId();
                return span1TraceLevel.compareTo(span2TraceLevel);
            }
        });

        Map<String, SpanEntry> spanEntryMap = mergeSpanDataSet(spanList);
        for (Map.Entry<String, SpanEntry> entry : spanEntryMap.entrySet()) {
            ChainNode chainNode = new ChainNode();
            SpanNodeProcessFilter filter = SpanNodeProcessChain.getProcessChainByCallType(entry.getValue().getSpanType());
            filter.doFilter(entry.getValue(), chainNode, costMap);
            chainInfo.addNodes(chainNode);
        }

        chainInfo.generateChainToken();
        HBaseUtil.saveData(key, chainInfo);
        return chainInfo;
    }

    private static Map<String, SpanEntry> mergeSpanDataSet(List<Span> spanList) {
        Map<String, SpanEntry> spanEntryMap = new LinkedHashMap<String, SpanEntry>();
        for (int i = spanList.size() - 1; i >= 0; i--) {
            Span span = spanList.get(i);
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
