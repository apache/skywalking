package com.ai.cloud.skywalking.analysis.mapper.util;

import com.ai.cloud.skywalking.analysis.chainbuild.ChainBuildMapper;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.protocol.Span;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.*;

/**
 * Created by xin on 16-5-13.
 */
public class Convert {

    private static final Map<String, String> traceIds = new HashMap<>();

    public static List<ChainInfo> convert(ResultScanner resultScanner) {
        List<ChainInfo> chainInfos = new ArrayList<ChainInfo>();
        for (Result result : resultScanner) {
            try {
                List<Span> spanList = new ArrayList<Span>();
                for (Cell cell : result.rawCells()) {
                    spanList.add(new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())));
                }

                if (spanList.size() == 0 || spanList.size() > 2000) {
                    continue;
                }

                ChainInfo chainInfo = ChainBuildMapper.spanToChainInfo(Bytes.toString(result.getRow()), spanList);
                chainInfos.add(chainInfo);

                traceIds.put(spanList.get(0).getTraceId(), chainInfo.getCID());
            } catch (Exception e) {
                continue;
            }
        }

        System.out.println(traceIds.size());

        return chainInfos;
    }
}
