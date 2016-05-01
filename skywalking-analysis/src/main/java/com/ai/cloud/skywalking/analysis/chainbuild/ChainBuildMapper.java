package com.ai.cloud.skywalking.analysis.chainbuild;

import com.ai.cloud.skywalking.analysis.chainbuild.exception.Tid2CidECovertException;
import com.ai.cloud.skywalking.analysis.chainbuild.filter.SpanNodeProcessChain;
import com.ai.cloud.skywalking.analysis.chainbuild.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.po.SummaryType;
import com.ai.cloud.skywalking.analysis.chainbuild.util.SubLevelSpanCostCounter;
import com.ai.cloud.skywalking.analysis.chainbuild.util.VersionIdentifier;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import com.ai.cloud.skywalking.protocol.Span;
import com.google.gson.Gson;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

public class ChainBuildMapper extends TableMapper<Text, Text> {

    private Logger logger = LogManager.getLogger(ChainBuildMapper.class);

    @Override
    protected void setup(Context context) throws IOException,
            InterruptedException {
        ConfigInitializer.initialize();
    }

    @Override
    protected void map(ImmutableBytesWritable key, Result value, Context context)
            throws IOException, InterruptedException {
        if (!VersionIdentifier.enableAnaylsis(Bytes.toString(key.get()))) {
            return;
        }

        List<Span> spanList = new ArrayList<Span>();
        ChainInfo chainInfo = null;
        try {
            for (Cell cell : value.rawCells()) {
                Span span = new Span(Bytes.toString(cell.getValueArray(),
                        cell.getValueOffset(), cell.getValueLength()));
                spanList.add(span);
            }
            if (spanList.size() == 0) {
                throw new Tid2CidECovertException("tid["
                        + Bytes.toString(key.get()) + "] has no span data.");
            }

            chainInfo = spanToChainInfo(Bytes.toString(key.get()), spanList);
            logger.debug("convert tid[" + Bytes.toString(key.get())
                    + "] to chain with cid[" + chainInfo.getCID() + "].");
            if (chainInfo.getCallEntrance() != null && chainInfo.getCallEntrance().length() > 0) {
                context.write(
                        new Text(chainInfo.getCallEntrance() + "@#!" + SummaryType.MINUTER.getValue()), new Text(new Gson().toJson(chainInfo)));
                context.write(
                        new Text(chainInfo.getCallEntrance() + "@#!" + SummaryType.HOUR.getValue()), new Text(new Gson().toJson(chainInfo)));
                context.write(
                        new Text(chainInfo.getCallEntrance() + "@#!" + SummaryType.DAY.getValue()), new Text(new Gson().toJson(chainInfo)));
                context.write(
                        new Text(chainInfo.getCallEntrance() + "@#!" + SummaryType.MONTH.getValue()), new Text(new Gson().toJson(chainInfo)));
            }
        } catch (Exception e) {
            logger.error("Failed to mapper call chain[" + key.toString() + "]",
                    e);
        }
    }

    public static ChainInfo spanToChainInfo(String tid, List<Span> spanList) {
        SubLevelSpanCostCounter costMap = new SubLevelSpanCostCounter();
        ChainInfo chainInfo = new ChainInfo(tid);
        Collections.sort(spanList, new Comparator<Span>() {
            @Override
            public int compare(Span span1, Span span2) {
                String span1TraceLevel = span1.getParentLevel() + "."
                        + span1.getLevelId();
                String span2TraceLevel = span2.getParentLevel() + "."
                        + span2.getLevelId();
                return span1TraceLevel.compareTo(span2TraceLevel);
            }
        });

        Map<String, SpanEntry> spanEntryMap = mergeSpanDataSet(spanList);
        for (Map.Entry<String, SpanEntry> entry : spanEntryMap.entrySet()) {
            ChainNode chainNode = new ChainNode();
            SpanNodeProcessFilter filter = SpanNodeProcessChain
                    .getProcessChainByCallType(entry.getValue().getSpanType());
            filter.doFilter(entry.getValue(), chainNode, costMap);
            chainInfo.addNodes(chainNode);
        }
        chainInfo.generateChainToken();
        return chainInfo;
    }

    private static Map<String, SpanEntry> mergeSpanDataSet(List<Span> spanList) {
        Map<String, SpanEntry> spanEntryMap = new LinkedHashMap<String, SpanEntry>();
        for (int i = spanList.size() - 1; i >= 0; i--) {
            Span span = spanList.get(i);
            SpanEntry spanEntry = spanEntryMap.get(span.getParentLevel() + "."
                    + span.getLevelId());
            if (spanEntry == null) {
                spanEntry = new SpanEntry();
                spanEntryMap.put(
                        span.getParentLevel() + "." + span.getLevelId(),
                        spanEntry);
            }
            spanEntry.setSpan(span);
        }
        return spanEntryMap;
    }
}
