package com.ai.cloud.skywalking.reciever.thread;

import com.ai.cloud.skywalking.reciever.persistance.PersistenceThread;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by astraea on 2015/11/5.
 */
public class PersistenceThreadTest {

    @Test
    public void testDealDataFile() throws InterruptedException {
        new PersistenceThread().start();
        Thread.sleep(500000L);
    }

    @Test
    public void test() {
        System.out.println(Integer.valueOf(30 * 1024* 1024));
    }

    @Test
    public  void testFile() throws IOException {
        File file = new File("D:\\test-data\\data\\buffer", "1446801421453-e10f3cc6279d48bebbd03fc3938ad665");
        FileWriter writer = new FileWriter(file,true);
        writer.write("ssss");
        writer.flush();
        writer.close();
       System.out.println(FileUtils.deleteQuietly(file));
    }
}