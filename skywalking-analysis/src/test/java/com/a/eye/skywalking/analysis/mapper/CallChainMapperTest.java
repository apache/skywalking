package com.a.eye.skywalking.analysis.mapper;


import com.a.eye.skywalking.analysis.config.HBaseTableMetaData;
import com.a.eye.skywalking.analysis.mapper.util.Convert;
import com.a.eye.skywalking.analysis.chainbuild.po.ChainInfo;
import com.a.eye.skywalking.analysis.mapper.util.HBaseUtils;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class CallChainMapperTest {

    private static Connection connection = HBaseUtils.getConnection();

    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss");

    private static final int EXCEPT_CHAIN_INFO_SIZE = 124985;


    public static void main(String[] args) throws Exception {
        Connection connection = HBaseUtils.getConnection();
        Table table = connection.getTable(TableName.valueOf
                (HBaseTableMetaData.TABLE_CALL_CHAIN.TABLE_NAME));
        Scan scan = new Scan();
        //2016-04-16/19:49:25 to 2016-05-16/19:39:25
        Date startDate = simpleDateFormat.parse("2016-04-16/19:49:25");
        Date endDate = simpleDateFormat.parse("2016-05-16/19:39:25");
        scan.setBatch(2001);
        scan.setTimeRange(startDate.getTime(), endDate.getTime());

        List<ChainInfo> chainInfos = Convert.convert(table.getScanner(scan));

        if (EXCEPT_CHAIN_INFO_SIZE != chainInfos.size()) {
            System.out.println("except size :" + EXCEPT_CHAIN_INFO_SIZE + " accutal size:" + chainInfos.size());
            System.exit(-1);
        }
    }
}
