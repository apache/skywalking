package com.ai.cloud.skywalking.analysis.mapper;

import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.protocol.CallType;
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
import java.util.HashMap;
import java.util.Map;

public class CallChainMapper extends TableMapper<Text, ChainInfo> {
    private Logger logger = LoggerFactory.getLogger(CallChainMapper.class.getName());

    @Override
    protected void map(ImmutableBytesWritable key, Result value, Context context) throws IOException,
            InterruptedException {
        Map<String, Long> costMap = new HashMap<String, Long>();

        ChainInfo chainInfo = new ChainInfo();
        String traceId = key.toString();
        logger.info("Begin to deal Trace[" + traceId + "]...");
        for (Cell cell : value.rawCells()) {
            ChainNode node = new ChainNode();
            Span span = new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()));

            node.setLevelId(span.getLevelId());
            node.setParentLevelId(span.getParentLevel());
            node.setViewPoint(convertViewPoint(span));
            node.setCost(span.getCost());
            node.setCallType(span.getCallType());
            if (span.isReceiver()) {
                costMap.put(span.getParentLevel() + "." + span.getLevelId() + "-S", span.getCost());
                continue;
            }

            if (costMap.containsKey(span.getParentLevel())) {
                costMap.put(span.getParentLevel(), costMap.get(span.getParentLevel()).longValue() + span.getCost());
            } else {
                costMap.put(span.getParentLevel(), 0L);
            }

            chainInfo.getNodes().add(node);
        }

        for (ChainNode node : chainInfo.getNodes()) {
            CallType callType = CallType.valueOf(node.getCallType());
            switch (callType) {
                case ASYNC:

                    break;
                case SYNC:

                    break;
                case LOCAL:

                    break;
            }
        }

        //入Mysql库
        //入HBase库
        //找到首个Node的MD5
        //拼接MR任务的Key
        context.write(new Text(""), chainInfo);
    }

    private String convertViewPoint(Span span) {
        return null;
    }
}
