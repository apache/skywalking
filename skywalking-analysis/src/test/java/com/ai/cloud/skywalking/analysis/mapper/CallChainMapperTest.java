package com.ai.cloud.skywalking.analysis.mapper;


import com.ai.cloud.skywalking.analysis.chainbuild.ChainBuildMapper;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
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

    public static void main(String[] args) throws Exception {
        ConfigInitializer.initialize();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss");
        Date startDate = simpleDateFormat.parse("2016-04-22/23:57:03");
        Date endDate = simpleDateFormat.parse("2016-05-02/23:47:03");
        Scan scan = new Scan();
        scan.setTimeRange(startDate.getTime(), endDate.getTime());
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN.TABLE_NAME));
        ResultScanner result = table.getScanner(scan);
        int count = 0;
        for (Result result1 : result) {
            count++;
        }
        System.out.println(count);
    }
}