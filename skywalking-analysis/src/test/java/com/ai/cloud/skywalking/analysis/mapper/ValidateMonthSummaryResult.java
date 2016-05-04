package com.ai.cloud.skywalking.analysis.mapper;

import com.ai.cloud.skywalking.analysis.chainbuild.ChainBuildMapper;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.ai.cloud.skywalking.analysis.mapper.util.HBaseUtils;
import com.ai.cloud.skywalking.protocol.Span;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ValidateMonthSummaryResult {
    public static void main(String[] args) throws IOException {
        Connection connection = HBaseUtils.getConnection();
        Table table = connection.getTable(TableName.valueOf
                (HBaseTableMetaData.TABLE_CALL_CHAIN.TABLE_NAME));
        Scan scan = new Scan();
        Filter filter = new SingleColumnValueFilter(HBaseTableMetaData.TABLE_CALL_CHAIN.FAMILY_NAME.getBytes(),
                "0-S".getBytes(), CompareFilter.CompareOp.EQUAL,
                "http://hire.asiainfo.com/Aisse-Mobile-Web/aisseWorkUser/queryProsonLoding".getBytes());
        scan.setFilter(filter);
        ResultScanner resultScanner = table.getScanner(scan);

        List<ChainInfo> chainInfos = new ArrayList<ChainInfo>();

        for (Result result : resultScanner) {
            List<Span> spanList = new ArrayList<Span>();
            for (Cell cell : result.rawCells()) {
                spanList.add(new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())));
            }

            chainInfos.add(ChainBuildMapper.spanToChainInfo(Bytes.toString(result.getRow()), spanList));
        }

        Map<String, Integer> totalCallSize = new HashMap<String, Integer>();
        Map<String, Float> totalCallTimes = new HashMap<>();
        for (ChainInfo chainInfo : chainInfos) {
            for (ChainNode chainNode : chainInfo.getNodes()) {
                Integer totalCall = totalCallSize.get(chainNode.getViewPoint());
                if (totalCallSize == null) {
                    totalCall = 0;
                }
                totalCallSize.put(chainNode.getTraceLevelId(), ++totalCall);

                Float totalCallTime = totalCallTimes.get(chainNode.getViewPoint());
                if (totalCallTime == null) {
                    totalCallTime = 0F;
                }
                totalCallTimes.put(chainNode.getTraceLevelId(), (totalCallTime + chainNode.getCost()));
            }
        }
    }
}
