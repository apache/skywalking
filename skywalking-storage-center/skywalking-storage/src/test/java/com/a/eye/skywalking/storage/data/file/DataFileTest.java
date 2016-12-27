package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.model.Tag;
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
    public void testWriteFile() {
        DataFile dataFile = new DataFile();

        IndexMetaInfo info = null;
        RequestSpan span = RequestSpan.newBuilder().putTags(Tag.USER_NAME.toString(), "1").putTags(Tag.APPLICATION_CODE.toString(), "app").build();

        try {
            info = dataFile.write(new RequestSpanData(span));
        } finally {
            dataFile.flush();
        }

        RequestSpan newSpan = SpanDataBuilder.buildRequestSpan(dataFile.read(info.getOffset(), info.getLength()));

        Assert.assertEquals("1", newSpan.getTagsMap().get(Tag.USER_NAME.key()));
    }
}
