package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.protocol.AckSpan;
import com.ai.cloud.skywalking.protocol.RequestSpan;
import com.ai.cloud.skywalking.protocol.exception.ConvertFailedException;
import com.ai.cloud.skywalking.web.dto.TraceNodeInfo;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.*;

public class SpanDataHandler {
    private Map<String, RequestSpan> levelIdToRequestSpans;
    private Map<String, AckSpan>     levelIdToAckSpans;

    public SpanDataHandler() {
        levelIdToRequestSpans = new HashMap<String, RequestSpan>();
        levelIdToAckSpans = new HashMap<String, AckSpan>();
    }

    public void addSpan(Cell cell) throws ConvertFailedException {
        if (cell != null && cell.getValueArray().length > 0) {
            String colId =
                    Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength());

            if (colId.endsWith("-ACK")) {
                levelIdToAckSpans.put(colId.substring(0, colId.length() - 4), convertACKSpan(CellUtil.cloneValue(cell)));
            } else {
                levelIdToRequestSpans.put(colId, convertRequestSpan(CellUtil.cloneValue(cell)));
            }
        }
    }

    private RequestSpan convertRequestSpan(byte[] originData) throws ConvertFailedException {
        return new RequestSpan(originData);
    }

    private AckSpan convertACKSpan(byte[] originData) throws ConvertFailedException {
        return new AckSpan(originData);
    }

    public Map<String, TraceNodeInfo> merge() {
        Map<String,TraceNodeInfo> traceNodeInfos = new HashMap<String,TraceNodeInfo>();
        for (Map.Entry<String, RequestSpan> entry : levelIdToRequestSpans.entrySet()){
            TraceNodeInfo traceNodeInfo = new TraceNodeInfo(entry.getValue(), levelIdToAckSpans.get(entry.getKey()));
            traceNodeInfos.put(entry.getKey(),traceNodeInfo);
        }

        return traceNodeInfos;
    }

}
