package com.a.eye.skywalking.analysis.mapper;

import com.a.eye.skywalking.analysis.chainbuild.po.ChainInfo;
import com.a.eye.skywalking.analysis.chainbuild.po.ChainNode;
import com.a.eye.skywalking.analysis.config.HBaseTableMetaData;
import com.a.eye.skywalking.analysis.mapper.util.Convert;
import com.a.eye.skywalking.analysis.mapper.util.HBaseUtils;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ValidateSpecialTimeSummaryResult {

    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss");


    public static void main(String[] args) throws IOException, ParseException {
        Connection connection = HBaseUtils.getConnection();
        Table table = connection.getTable(TableName.valueOf
                (HBaseTableMetaData.TABLE_CALL_CHAIN.TABLE_NAME));
        Scan scan = new Scan();
        Date startDate = simpleDateFormat.parse("2016-04-13/16:59:24");
        Date endDate = simpleDateFormat.parse("2016-05-13/16:49:24");
        scan.setMaxVersions();
        scan.setTimeRange(startDate.getTime(), endDate.getTime());
        Filter filter = new SingleColumnValueFilter(HBaseTableMetaData.TABLE_CALL_CHAIN.FAMILY_NAME.getBytes(),
                "0-S".getBytes(), CompareFilter.CompareOp.EQUAL,
                "http://hire.asiainfo.com/Aisse-Mobile-Web/aisseWorkUser/queryProsonLoding".getBytes());
        scan.setFilter(filter);

        List<ChainInfo> chainInfos = Convert.convert(table.getScanner(scan));


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
