package com.ai.cloud.skywalking.analysis.mapper;


import com.ai.cloud.skywalking.analysis.chainbuild.ChainBuildMapper;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.ai.cloud.skywalking.analysis.mapper.util.Convert;
import com.ai.cloud.skywalking.analysis.mapper.util.HBaseUtils;
import com.ai.cloud.skywalking.protocol.Span;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
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
        //2016-04-13/16:59:24 to 2016-05-13/16:49:24
        Date startDate = simpleDateFormat.parse("2016-04-13/16:59:24");
        Date endDate = simpleDateFormat.parse("2016-05-13/16:49:24");
        scan.setBatch(2001);
        scan.setTimeRange(startDate.getTime(), endDate.getTime());

        List<ChainInfo> chainInfos = Convert.convert(table.getScanner(scan));

        if (EXCEPT_CHAIN_INFO_SIZE != chainInfos.size()) {
            System.out.println("except size :" + EXCEPT_CHAIN_INFO_SIZE + " accutal size:" + chainInfos.size());
            System.exit(-1);
        }
    }
}