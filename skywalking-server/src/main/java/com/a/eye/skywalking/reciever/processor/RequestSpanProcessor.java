package com.a.eye.skywalking.reciever.processor;

import com.a.eye.skywalking.reciever.conf.Config;
import com.a.eye.skywalking.reciever.util.HBaseUtil;
import com.a.eye.skywalking.reciever.util.SpanUtil;
import com.a.eye.skywalking.protocol.RequestSpan;
import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.ArrayList;
import java.util.List;

@DefaultProcessor
public class RequestSpanProcessor extends AbstractSpanProcessor {


    @Override
    public void doAlarm(List<AbstractDataSerializable> serializedObjects) {
        //DO Nothing
    }

    @Override
    public void doSaveHBase(Connection connection, List<AbstractDataSerializable> serializedObjects) {
        List<Put> puts = new ArrayList<Put>();
        // convert to put
        String columnName;
        for (AbstractDataSerializable serializedObject : serializedObjects) {
            RequestSpan requestSpan = (RequestSpan) serializedObject;
            Put put = new Put(Bytes.toBytes(requestSpan.getTraceId()), SpanUtil.getTSBySpanTraceId(requestSpan.getTraceId()));
            if (StringUtils.isEmpty(requestSpan.getParentLevel().trim())) {
                columnName = requestSpan.getLevelId() + "";
            } else {
                columnName = requestSpan.getParentLevel() + "." + requestSpan.getLevelId();
            }
            put.addColumn(Bytes.toBytes(Config.HBaseConfig.TraceDataTable.FAMILY_COLUMN_NAME), Bytes.toBytes(columnName),
                    requestSpan.getData());
            puts.add(put);
        }
        // save
        HBaseUtil.batchSavePuts(connection, Config.HBaseConfig.TraceDataTable.TABLE_NAME, puts);
    }

    @Override
    public int getProtocolType() {
        return 1;
    }

}
