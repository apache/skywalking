package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.protocol.RequestSpan;
import com.ai.cloud.skywalking.protocol.proto.TraceProtocol;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

import static org.apache.hadoop.hbase.util.Bytes.toBytes;
import static org.junit.Assert.*;

/**
 * Created by xin on 16/8/5.
 */
public class SpanDataHandlerTest {

    private Configuration configuration;
    private Connection    connection;

    @Test
    public void addSpan0() throws Exception {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();
            configuration.set("hbase.zookeeper.quorum", "swhbaseenv");
            configuration.set("hbase.zookeeper.property.clientPort", "2181");
        }
        connection = ConnectionFactory.createConnection(configuration);

        Admin admin = connection.getAdmin();
        if (!admin.tableExists(TableName.valueOf("trace-data"))){
            HTableDescriptor descriptor = new HTableDescriptor(TableName.valueOf("trace-data"));
            HColumnDescriptor family = new HColumnDescriptor(toBytes("call-chain"));
            descriptor.addFamily(family);
            admin.createTable(descriptor);
        }

        TraceProtocol.RequestSpan requestSpan = TraceProtocol.RequestSpan.newBuilder().setUserId("1")
                .setStartDate(System.currentTimeMillis() - 10 * 1000).setViewPointId("test").setAgentId("1")
                .setApplicationId("test").setCallType("w").setLevelId(0).setParentLevel("test").setSpanType(1)
                .setSpanTypeDesc("test").setTraceId("test").build();




        Put put = new Put(Bytes.toBytes(requestSpan.getTraceId()));
        put.addColumn(Bytes.toBytes("call-chain"), Bytes.toBytes("0"), requestSpan.toByteArray());
        for (byte b : requestSpan.toByteArray()){
            System.out.print(b  + " ");
        }
        Table table = connection.getTable(TableName.valueOf("trace-data"));
        table.put(put);

        Get get = new Get(requestSpan.getTraceId().getBytes());
        Result result = table.get(get);
       for (Cell cell : result.rawCells()){
           byte[] bytes = CellUtil.cloneValue(cell);
           for (byte b : bytes){
               System.out.print(b + " ");
           }
          TraceProtocol.RequestSpan requestSpan1 =  TraceProtocol.RequestSpan.parseFrom(cell.getValueArray());
       }


    }

    @Test
    public void addSpan() throws Exception {
        TraceProtocol.RequestSpan requestSpan =
                TraceProtocol.RequestSpan.newBuilder().setLevelId(0).setTraceId("test").setCallType("1")
                        .setParentLevel("").setApplicationId("1").setSpanType(1).setSpanTypeDesc("web").setAgentId("1")
                        .setViewPointId("tst").setStartDate(System.currentTimeMillis() - 10000).setUserId("1").build();
        new RequestSpan(requestSpan.toByteArray());
    }

}
