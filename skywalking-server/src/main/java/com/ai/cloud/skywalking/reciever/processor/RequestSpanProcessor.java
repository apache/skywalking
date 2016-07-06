package com.ai.cloud.skywalking.reciever.processor;

import com.ai.cloud.skywalking.protocol.RequestSpan;
import com.ai.cloud.skywalking.protocol.common.AbstractDataSerializable;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.util.HBaseUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.ArrayList;
import java.util.List;

import static com.ai.cloud.skywalking.reciever.util.SpanUtil.getTSBySpanTraceId;

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
            Put put = new Put(Bytes.toBytes(requestSpan.getTraceId()), getTSBySpanTraceId(requestSpan.getTraceId()));
            if (StringUtils.isEmpty(requestSpan.getParentLevel().trim())) {
                columnName = requestSpan.getLevelId() + "";
            } else {
                columnName = requestSpan.getParentLevel() + "." + requestSpan.getLevelId();
            }
            put.addColumn(Bytes.toBytes(Config.HBaseConfig.FAMILY_COLUMN_NAME), Bytes.toBytes(columnName), requestSpan.getData());

            puts.add(put);
        }
        // save
        HBaseUtil.batchSavePuts(connection, Config.HBaseConfig.TABLE_NAME, puts);
    }

    @Override
    public int getType() {
        return 1;
    }

}
