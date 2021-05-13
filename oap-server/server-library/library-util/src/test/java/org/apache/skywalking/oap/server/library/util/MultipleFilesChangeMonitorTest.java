/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.library.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class MultipleFilesChangeMonitorTest {
    private static String FILE_NAME = "FileChangeMonitorTest.tmp";

    @Test
    public void test() throws InterruptedException, IOException {
        StringBuilder content = new StringBuilder();
        MultipleFilesChangeMonitor monitor = new MultipleFilesChangeMonitor(
            1, new MultipleFilesChangeMonitor.FilesChangedNotifier() {

            @Override
            public void filesChanged(final List<byte[]> readableContents) {
                Assert.assertEquals(2, readableContents.size());
                Assert.assertNull(readableContents.get(1));
                try {
                    content.delete(0, content.length());
                    content.append(new String(readableContents.get(0), 0, readableContents.get(0).length, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }, FILE_NAME, "XXXX_NOT_EXIST.SW");

        monitor.start();

        File file = new File(FILE_NAME);
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
        bos.write("test context".getBytes(Charset.forName("UTF-8")));
        bos.flush();
        bos.close();

        int countDown = 40;
        boolean notified = false;
        boolean notified2 = false;
        while (countDown-- > 0) {
            if ("test context".equals(content.toString())) {
                file = new File(FILE_NAME);
                bos = new BufferedOutputStream(new FileOutputStream(file));
                bos.write("test context again".getBytes(Charset.forName("UTF-8")));
                bos.flush();
                bos.close();
                notified = true;
            } else if ("test context again".equals(content.toString())) {
                notified2 = true;
                break;
            }
            Thread.sleep(500);
        }
        Assert.assertTrue(notified);
        Assert.assertTrue(notified2);
    }

    @BeforeClass
    @AfterClass
    public static void cleanup() {
        File file = new File(FILE_NAME);
        if (file.exists() && file.isFile()) {
            file.delete();
        }
    }
}
