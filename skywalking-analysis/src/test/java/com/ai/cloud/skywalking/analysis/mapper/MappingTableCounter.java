package com.ai.cloud.skywalking.analysis.mapper;

import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.ai.cloud.skywalking.analysis.mapper.util.HBaseUtils;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by xin on 16-5-16.
 */
public class MappingTableCounter {

    public static Set<String> getTraceMappingCount() throws IOException {
        Connection connection = HBaseUtils.getConnection();
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_TRACE_ID_AND_CID_MAPPING.TABLE_NAME));
        ResultScanner resultScanner = table.getScanner(new Scan());
        Set<String> traceIds = new HashSet<String>();
        for (Result result :resultScanner){
           traceIds.add(Bytes.toString(result.getRow()));
        }

        return traceIds;
    }
}
