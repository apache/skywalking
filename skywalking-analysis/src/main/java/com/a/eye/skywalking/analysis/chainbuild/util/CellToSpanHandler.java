package com.a.eye.skywalking.analysis.chainbuild.util;

import com.a.eye.skywalking.protocol.AckSpan;
import com.a.eye.skywalking.protocol.FullSpan;
import com.a.eye.skywalking.protocol.RequestSpan;
import com.a.eye.skywalking.protocol.exception.ConvertFailedException;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.HashMap;
import java.util.Map;

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

    public Map<String, FullSpan> handle() {
        Map<String,FullSpan> fullSpans = new HashMap<String,FullSpan>();
        for (Map.Entry<String, RequestSpan> entry : levelIdToRequestSpans.entrySet()){
            FullSpan traceNodeInfo = new FullSpan(entry.getValue(), levelIdToAckSpans.get(entry.getKey()));
            fullSpans.put(entry.getKey(),traceNodeInfo);
        }

        return fullSpans;
    }


    public Map<String, RequestSpan> getRequestSpans() {
        return levelIdToRequestSpans;
    }

    public Map<String, AckSpan> getAckSpans() {
        return levelIdToAckSpans;
    }



}
