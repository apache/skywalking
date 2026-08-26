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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MultipleFilesChangeMonitorTest {
    private static final String FILE_NAME = "FileChangeMonitorTest.tmp";
    private static final String PERIOD_FILE_NAME = "FileChangeMonitorPeriodTest.tmp";

    @Test
    public void test() throws InterruptedException, IOException {
        StringBuilder content = new StringBuilder();
        MultipleFilesChangeMonitor monitor = new MultipleFilesChangeMonitor(
            1, readableContents -> {
                assertEquals(2, readableContents.size());
                assertNull(readableContents.get(1));
            content.delete(0, content.length());
            content.append(new String(readableContents.get(0), 0, readableContents.get(0).length, StandardCharsets.UTF_8));
        }, FILE_NAME, "XXXX_NOT_EXIST.SW");

        monitor.start();

        File file = new File(FILE_NAME);
        BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(file.toPath()));
        bos.write("test context".getBytes(StandardCharsets.UTF_8));
        bos.flush();
        bos.close();

        int countDown = 40;
        boolean notified = false;
        boolean notified2 = false;
        while (countDown-- > 0) {
            if ("test context".equals(content.toString())) {
                file = new File(FILE_NAME);
                bos = new BufferedOutputStream(Files.newOutputStream(file.toPath()));
                bos.write("test context again".getBytes(StandardCharsets.UTF_8));
                bos.flush();
                bos.close();
                notified = true;
            } else if ("test context again".equals(content.toString())) {
                notified2 = true;
                break;
            }
            Thread.sleep(500);
        }
        Assertions.assertTrue(notified);
        Assertions.assertTrue(notified2);
    }

    /**
     * The watching period has to be honoured, not just accepted. {@code lastCheckTimestamp} was never stamped, so
     * the guard in checkAndNotify() compared against 0 and always passed — every monitor in the JVM re-stat'd its
     * files on each 200ms tick of the shared scheduler, and the period argument did nothing.
     */
    @Test
    public void shouldNotCheckMoreOftenThanTheWatchingPeriod() throws Exception {
        final AtomicInteger notifications = new AtomicInteger();
        final MultipleFilesChangeMonitor monitor = new MultipleFilesChangeMonitor(
            2, readableContents -> notifications.incrementAndGet(), PERIOD_FILE_NAME);

        write(PERIOD_FILE_NAME, "first");
        try {
            // start() runs the first check inline, and lastCheckTimestamp starts at 0 so it must not be throttled.
            monitor.start();
            assertEquals(1, notifications.get());

            // Slept long enough that the new content lands on a different modified timestamp on any filesystem
            // granularity, but still inside the 2s period.
            Thread.sleep(1100);
            write(PERIOD_FILE_NAME, "second");
            MultipleFilesChangeMonitor.scanChanges();
            assertEquals(1, notifications.get());

            Thread.sleep(1100);
            MultipleFilesChangeMonitor.scanChanges();
            assertEquals(2, notifications.get());
        } finally {
            monitor.stop();
        }
    }

    private static void write(String fileName, String content) throws IOException {
        try (BufferedOutputStream bos =
                 new BufferedOutputStream(Files.newOutputStream(new File(fileName).toPath()))) {
            bos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    @BeforeAll
    @AfterAll
    public static void cleanup() {
        for (final String fileName : new String[] {FILE_NAME, PERIOD_FILE_NAME}) {
            File file = new File(fileName);
            if (file.exists() && file.isFile()) {
                file.delete();
            }
        }
    }
}
