package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.spandata.RequestSpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.util.PathResolver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by xin on 2016/11/15.
 */
public class DataFileWriterTest {

    private DataFileWriter writer;

    @Before
    public void setUp() {
        Config.DataFile.PATH = "/tmp";
        Config.DataFile.SIZE = 10;
        writer = new DataFileWriter();
    }

    @Test
    public void testConvertFile() throws Exception {
        List<SpanData> spanData = new ArrayList<>();
        spanData.add(new RequestSpanData(
                RequestSpan.newBuilder().setTraceId("test-traceId").setStartDate(System.currentTimeMillis())
                        .setProcessNo("7777").setLevelId(10).setParentLevel("0.0.0").setAddress("127.0.0.1").build()));
        writer.write(spanData);

        writer.write(spanData);
        File dir = new File(PathResolver.getAbsolutePath(Config.DataFile.PATH));
        assertEquals(2, dir.listFiles().length);
    }


    @After
    public void tearUp() throws IOException {
        File dir = new File(PathResolver.getAbsolutePath(Config.DataFile.PATH));
        for (File file : dir.listFiles()) {
            file.delete();
        }

        dir.delete();
    }

}
