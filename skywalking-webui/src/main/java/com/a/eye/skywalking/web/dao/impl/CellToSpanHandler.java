package com.a.eye.skywalking.web.dao.impl;

import com.a.eye.skywalking.protocol.AckSpan;
import com.a.eye.skywalking.protocol.RequestSpan;
import com.a.eye.skywalking.protocol.exception.ConvertFailedException;
import com.a.eye.skywalking.web.dto.TraceNodeInfo;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.*;

public class CellToSpanHandler {
    private Map<String, RequestSpan> levelIdToRequestSpans;
    private Map<String, AckSpan>     levelIdToAckSpans;

    public CellToSpanHandler() {
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
        return RequestSpan.convert(originData);
    }

    private AckSpan convertACKSpan(byte[] originData) throws ConvertFailedException {
        return AckSpan.convert(originData);
    }

    public Map<String, TraceNodeInfo> handle() {
        Map<String,TraceNodeInfo> traceNodeInfos = new HashMap<String,TraceNodeInfo>();
        for (Map.Entry<String, RequestSpan> entry : levelIdToRequestSpans.entrySet()){
            TraceNodeInfo traceNodeInfo = new TraceNodeInfo(entry.getValue(), levelIdToAckSpans.get(entry.getKey()));
            traceNodeInfos.put(entry.getKey(),traceNodeInfo);
        }

        return traceNodeInfos;
    }


    public Map<String, RequestSpan> getRequestSpans() {
        return levelIdToRequestSpans;
    }

    public Map<String, AckSpan> getAckSpans() {
        return levelIdToAckSpans;
    }



}
