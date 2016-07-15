package com.ai.cloud.skywalking.reciever.processor;

import com.ai.cloud.skywalking.protocol.AckSpan;
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

@DefaultProcessor
public class AckSpanProcessor extends AbstractSpanProcessor {

    @Override
    public void doAlarm(List<AbstractDataSerializable> serializedObjects) {

    }

    @Override
    public void doSaveHBase(Connection connection, List<AbstractDataSerializable> serializedObjects) {
        List<Put> puts = new ArrayList<Put>();
        // convert to put
        String columnName;
        for (AbstractDataSerializable serializedObject : serializedObjects) {
            AckSpan ackSpan = (AckSpan) serializedObject;
            Put put = new Put(Bytes.toBytes(ackSpan.getTraceId()), getTSBySpanTraceId(ackSpan.getTraceId()));
            if (StringUtils.isEmpty(ackSpan.getParentLevel().trim())) {
                columnName = ackSpan.getLevelId() + "";
            } else {
                columnName = ackSpan.getParentLevel() + "." + ackSpan.getLevelId();
            }
            // appending suffix
            columnName += "-ACK";

            put.addColumn(Bytes.toBytes(Config.HBaseConfig.FAMILY_COLUMN_NAME), Bytes.toBytes(columnName), ackSpan.getData());
            puts.add(put);
        }
        // save
        HBaseUtil.batchSavePuts(connection, Config.HBaseConfig.TABLE_NAME, puts);
    }

    @Override
    public int getProtocolType() {
        return 2;
    }
}
