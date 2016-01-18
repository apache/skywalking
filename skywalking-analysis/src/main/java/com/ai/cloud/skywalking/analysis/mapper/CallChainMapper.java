package com.ai.cloud.skywalking.analysis.mapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessChain;
import com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.model.CostMap;
import com.ai.cloud.skywalking.analysis.model.SpanEntry;
import com.ai.cloud.skywalking.analysis.util.HBaseUtil;
import com.ai.cloud.skywalking.protocol.Span;

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

        //TODO: sort spanList
        
        Map<String, SpanEntry> spanEntryMap = mergeSpanDataset(spanList);
        for (Map.Entry<String, SpanEntry> entry : spanEntryMap.entrySet()) {
            ChainNode chainNode = new ChainNode();
            SpanNodeProcessFilter filter = SpanNodeProcessChain.getProcessChainByCallType(entry.getValue().getSpanType());
            filter.doFilter(entry.getValue(), chainNode, costMap);
            chainInfo.addNodes(chainNode);
        }
        
        chainInfo.generateChainToken();
        
        HBaseUtil.saveData(key.toString(), chainInfo);

        context.write(new Text(chainInfo.getUserId() + ":" + chainInfo.getEntranceNodeToken()), chainInfo);
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
