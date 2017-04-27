package org.skywalking.apm.api.logging;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.skywalking.apm.api.conf.Config;
import org.skywalking.apm.api.conf.Constants;

import java.io.File;
import java.io.IOException;

/**
 * @author wusheng
 */
public class FileWriterTest {

    @BeforeClass
    public static void beforeTestFile() throws IOException {
        Config.Logging.MAX_FILE_SIZE = 10;
        File directory = new File("");
        Config.Logging.DIR = directory.getCanonicalPath() + Constants.PATH_SEPARATOR + "/log-test/";
    }

    @Test
    public void testWriteFile() throws InterruptedException {
        FileWriter writer = FileWriter.get();
        for (int i = 0; i < 100; i++) {
            writer.write("abcd");
        }

        Thread.sleep(10000L);
    }

    @AfterClass
    public static void clear() {
        Config.Logging.MAX_FILE_SIZE = 300 * 1024 * 1024;
        deleteDir(new File(Config.Logging.DIR));
        Config.Logging.DIR = "";
    }

    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }
}
