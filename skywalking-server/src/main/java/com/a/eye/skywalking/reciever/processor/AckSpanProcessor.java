package com.a.eye.skywalking.reciever.processor;

import com.a.eye.skywalking.reciever.conf.Config;
import com.a.eye.skywalking.reciever.processor.ackspan.alarm.ExecuteTimeChecker;
import com.a.eye.skywalking.reciever.processor.ackspan.alarm.ISpanChecker;
import com.a.eye.skywalking.reciever.util.HBaseUtil;
import com.a.eye.skywalking.protocol.AckSpan;
import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;
import com.a.eye.skywalking.reciever.processor.ackspan.alarm.ExceptionChecker;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.ArrayList;
import java.util.List;

import static com.a.eye.skywalking.reciever.util.SpanUtil.getTSBySpanTraceId;

@DefaultProcessor
public class AckSpanProcessor extends AbstractSpanProcessor {

    private List<ISpanChecker> checkList = new ArrayList<ISpanChecker>();

    public AckSpanProcessor() {
        if (Config.Alarm.Checker.TURN_ON_EXCEPTION_CHECKER)
            checkList.add(new ExceptionChecker());
        if (Config.Alarm.Checker.TURN_ON_EXECUTE_TIME_CHECKER)
            checkList.add(new ExecuteTimeChecker());
    }

    @Override
    public void doAlarm(List<AbstractDataSerializable> ackSpans) {
        for (AbstractDataSerializable ackSpan : ackSpans) {
            for (ISpanChecker checker : checkList) {
                checker.check((AckSpan) ackSpan);
            }
        }
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

            put.addColumn(Bytes.toBytes(Config.HBaseConfig.TraceDataTable.FAMILY_COLUMN_NAME), Bytes.toBytes(columnName),
                    ackSpan.getData());
            puts.add(put);
        }
        // save
        HBaseUtil.batchSavePuts(connection, Config.HBaseConfig.TraceDataTable.TABLE_NAME, puts);
    }

    @Override
    public int getProtocolType() {
        return 2;
    }
}
