package com.ai.cloud.skywalking.analysis.mapper.util;

import com.ai.cloud.skywalking.analysis.chainbuild.ChainBuildMapper;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.mapper.MappingTableCounter;
import com.ai.cloud.skywalking.protocol.Span;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.*;

/**
 * Created by xin on 16-5-13.
 */
public class Convert {

    private static final Map<String, String> traceIds = new HashMap<>();

    public static List<ChainInfo> convert(ResultScanner resultScanner) throws IOException {
        List<ChainInfo> chainInfos = new ArrayList<ChainInfo>();
        int count = 0, failedCount = 0, successCount = 0;
        for (Result result : resultScanner) {
            count++;
            try {
                List<Span> spanList = new ArrayList<Span>();
                for (Cell cell : result.rawCells()) {
                    spanList.add(new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())));
                }

                if (spanList.size() == 0 || spanList.size() > 2000) {
                    throw new RuntimeException("Failed to convert it");
                }

                ChainInfo chainInfo = ChainBuildMapper.spanToChainInfo(Bytes.toString(result.getRow()), spanList);
                chainInfos.add(chainInfo);

                traceIds.put(spanList.get(0).getTraceId(), chainInfo.getCID());
                successCount++;
            } catch (Exception e) {
                failedCount++;
                continue;
            }
        }

        System.out.println("count : " + count);
//        System.out.println("Success count " + traceIds.size());
        System.out.println("Failed count " + failedCount);
        System.out.println("Success count " + successCount);
        System.out.println("HBase :" + traceIds.size());


        Set<String> traceMapping = MappingTableCounter.getTraceMappingCount();
        for (String traceId : traceMapping){
            traceIds.remove(traceId);
        }

        System.out.println(traceIds);

        return chainInfos;
    }
}
