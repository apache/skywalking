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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * File change monitor is a disk file watcher. It keeps to watch the file `last modified timestamp`, after it changed,
 * fetch the new content of the file and check with the prev one. If content changed, it will notify the listener.
 *
 * File Change
 */
@RequiredArgsConstructor
@Slf4j
public class FileChangeMonitor {
    /**
     * The backend scheduler to trigger all file monitoring.
     */
    private static ScheduledFuture<?> FILE_MONITOR_TASK_SCHEDULER;
    /**
     * The list contains all monitors.
     */
    private static List<FileChangeMonitor> MONITOR_INSTANCES = new ArrayList<>();

    /**
     * The absolute path of the monitored file.
     */
    private final String filePath;
    /**
     * Trigger notification when file is not there.
     */
    private final boolean acceptFileNotExisting;
    /**
     * The period of watching thread checking the file status. Unit is the second.
     */
    private final long watchingPeriodInSec;
    /**
     * The notifier when file content changed.
     */
    private final FileChangedNotifier notifier;
    /**
     * The timestamp when last time do status checked.
     */
    private long lastCheckTimestamp = 0;
    /**
     * The last modify time of the {@link #filePath}
     */
    private long lastModifiedTimestamp = 0;

    /**
     * Start the file monitor for this instance.
     */
    public synchronized void start() {
        if (FILE_MONITOR_TASK_SCHEDULER == null) {
            FILE_MONITOR_TASK_SCHEDULER = Executors.newSingleThreadScheduledExecutor()
                                                   .scheduleAtFixedRate(
                                                       FileChangeMonitor::run, 1, 1,
                                                       TimeUnit.SECONDS
                                                   );
        }

        this.checkAndNotify();
        MONITOR_INSTANCES.add(this);
    }

    public synchronized void stop() {
        MONITOR_INSTANCES.remove(this);
    }

    /**
     * Check the file status, if changed, send the notification.
     */
    private void checkAndNotify() {
        if (System.currentTimeMillis() - lastCheckTimestamp < watchingPeriodInSec * 1000) {
            // Don't reach the period threshold, ignore this check.
            return;
        }
        File targetFile = new File(filePath);
        if (!targetFile.exists() && acceptFileNotExisting) {
            notifier.fileNotFound();
        }
        if (targetFile.isFile()) {
            long lastModified = targetFile.lastModified();

            if (lastModified != lastModifiedTimestamp) {
                try (FileInputStream fileInputStream = new FileInputStream(targetFile)) {
                    notifier.fileChanged(fileInputStream);
                } catch (FileNotFoundException e) {
                    log.error("The existed file turns to missing, watch file=" + filePath, e);
                } catch (IOException e) {
                    log.error("Read file failure, watch file=" + filePath, e);
                } finally {
                    lastModifiedTimestamp = lastModified;
                }
            }
        }
    }

    /**
     * Check all registered file.
     */
    private static void run() {
        MONITOR_INSTANCES.forEach(monitor -> {
            try {
                monitor.checkAndNotify();
            } catch (Throwable e) {
                log.error("Error happens during monitoring file=" + monitor.filePath, e);
            }
        });
    }

    /**
     * The callback when file changed.
     */
    public interface FileChangedNotifier {
        /**
         * Notify the new content by providing the file input stream
         *
         * @param readableStream points to the new content
         * @throws IOException if error happens during reading.
         */
        void fileChanged(InputStream readableStream) throws IOException;

        /**
         * Notify the event of file not found.
         */
        void fileNotFound();
    }

    /**
     * An implementation of {@link FileChangedNotifier}, it only triggers the notification with binary content changes,
     * rather than simple file modified timestamp changed.
     */
    public static abstract class ContentChangedNotifier implements FileChangedNotifier {
        private byte[] fileContent = null;

        @Override
        public void fileChanged(final InputStream readableStream) throws IOException {
            byte[] b = new byte[1024];
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            int c;
            while ((c = readableStream.read(b)) != -1) {
                os.write(b, 0, c);
            }
            byte[] newContent = os.toByteArray();
            if (!Arrays.equals(newContent, fileContent)) {
                fileContent = newContent;
                this.contentChanged(newContent);
            }
        }

        /**
         * Notify when the content are changed.
         *
         * @param newContent in the file.
         */
        protected abstract void contentChanged(byte[] newContent);
    }
}
