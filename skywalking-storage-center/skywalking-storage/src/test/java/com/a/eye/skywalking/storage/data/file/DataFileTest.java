package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;
import com.a.eye.skywalking.storage.data.spandata.RequestSpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanDataBuilder;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2016/11/29.
 */
public class DataFileTest {
    @Test
    public void testWriteFile(){
        DataFile dataFile = new DataFile();

        IndexMetaInfo info = null;
        for (int i = 0; i < 100; i++) {
            RequestSpan span = RequestSpan.newBuilder().setUsername("1").setApplicationCode("app").build();

            try {
                info = dataFile.write(new RequestSpanData(span));
            } finally {
                dataFile.flush();
            }

            RequestSpan newSpan = SpanDataBuilder.buildRequestSpan(dataFile.read(info.getOffset(), info.getLength()));

            Assert.assertEquals("1", newSpan.getUsername());
        }
    }
}
