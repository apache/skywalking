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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * MultipleFilesChangeMonitor provides the capability to detect file or multiple files changed. It provide second level
 * change detection and feedback mechanism.
 *
 * Due to memory cost, this monitor mechanism is not suitable for small files and usually being changed on the runtime
 * by user manually or 3rd party tool. Typical, these files are config information or authentication files.
 */
@Slf4j
public class MultipleFilesChangeMonitor {
    /**
     * The backend scheduler to trigger all file monitoring.
     */
    private static ScheduledFuture<?> FILE_MONITOR_TASK_SCHEDULER;
    private static ReentrantLock SCHEDULER_CHANGE_LOCK = new ReentrantLock();
    /**
     * The list contains all monitors.
     */
    private static List<MultipleFilesChangeMonitor> MONITOR_INSTANCES = new ArrayList<>();

    /**
     * The timestamp when last time do status checked.
     */
    private long lastCheckTimestamp = 0;
    /**
     * The period of watching thread checking the file status. Unit is the second.
     */
    private final long watchingPeriodInSec;
    private List<WatchedFile> watchedFiles;
    private FilesChangedNotifier notifier;

    /**
     * Create a new monitor for the given files
     *
     * @param watchingPeriodInSec The check period.
     * @param notifier            to accept the file changed notification.
     * @param files               to be monitored. If an element of list is NULL, the virtual(NULL) file is treated
     *                            unchangeable.
     */
    public MultipleFilesChangeMonitor(long watchingPeriodInSec,
                                      FilesChangedNotifier notifier,
                                      String... files) {
        watchedFiles = new ArrayList<>();
        this.watchingPeriodInSec = watchingPeriodInSec;
        this.notifier = notifier;
        for (final String file : files) {
            WatchedFile monitor;
            if (StringUtil.isEmpty(file)) {
                monitor = new NoopWatchedFile();
            } else {
                monitor = new WatchedFile(file);
            }
            watchedFiles.add(monitor);
        }
    }

    /**
     * Check file changed status, if so, send the notification.
     */
    private void checkAndNotify() {
        if (System.currentTimeMillis() - lastCheckTimestamp < watchingPeriodInSec * 1000) {
            // Don't reach the period threshold, ignore this check.
            return;
        }

        boolean isChanged = false;
        for (final WatchedFile watchedFile : watchedFiles) {
            isChanged = isChanged || watchedFile.detectContentChanged();
        }
        if (isChanged) {
            List<byte[]> contents = new ArrayList<>(watchedFiles.size());
            watchedFiles.forEach(file -> {
                contents.add(file.fileContent);
            });
            try {
                notifier.filesChanged(contents);
            } catch (Exception e) {
                log.error("Files=" + this + " notification process failure.", e);
            }
        }
    }

    /**
     * One file changed will cause all related files loaded from the disk again with lastModifiedTimestamp updated.
     */
    public static void scanChanges() {
        MONITOR_INSTANCES.forEach(group -> {
            try {
                group.checkAndNotify();
            } catch (Throwable t) {
                log.error("Files change detection failure, gourp = ", t);
            }
        });
    }

    /**
     * Start the change monitoring.
     */
    public void start() {
        SCHEDULER_CHANGE_LOCK.lock();
        try {
            if (FILE_MONITOR_TASK_SCHEDULER == null) {
                FILE_MONITOR_TASK_SCHEDULER = Executors.newSingleThreadScheduledExecutor()
                                                       .scheduleAtFixedRate(
                                                           MultipleFilesChangeMonitor::scanChanges, 1, 200,
                                                           TimeUnit.MILLISECONDS
                                                       );
            }

            if (MONITOR_INSTANCES.contains(this)) {
                throw new IllegalStateException("This FileChangeMonitor has been started.");
            }

            this.checkAndNotify();
            MONITOR_INSTANCES.add(this);
        } finally {
            SCHEDULER_CHANGE_LOCK.unlock();
        }
    }

    /**
     * Stop the change monitoring.
     */
    public void stop() {
        SCHEDULER_CHANGE_LOCK.lock();
        try {
            MONITOR_INSTANCES.remove(this);
        } finally {
            SCHEDULER_CHANGE_LOCK.unlock();
        }
    }

    @Override
    public String toString() {
        return "MultipleFilesChangeMonitor{" +
            "watchedFiles=" + watchedFiles +
            '}';
    }

    /**
     * The callback when files changed.
     */
    public interface FilesChangedNotifier {
        /**
         * Notify the new content by providing the file input stream for all files in this group.
         *
         * @param readableContents include the new contents. NULL if the file doesn't exist.
         */
        void filesChanged(List<byte[]> readableContents) throws Exception;
    }

    /**
     * WatchedFile represents a file change detector. It could detect the file changed based on modified time and file
     * content at the binary level. It load the file content into the memory as cache to do the comparison.
     */
    @RequiredArgsConstructor
    @Slf4j
    private static class WatchedFile {
        /**
         * The absolute path of the monitored file.
         */
        private final String filePath;
        /**
         * The last modify time of the {@link #filePath}
         */
        private long lastModifiedTimestamp = 0;
        /**
         * File content at the latest status.
         */
        private byte[] fileContent;

        /**
         * Detect the file content change, if yes, reload the file content into the memory as cached data.
         *
         * @return true if file content changed.
         */
        boolean detectContentChanged() {
            File targetFile = new File(filePath);
            if (!targetFile.exists()) {
                if (lastModifiedTimestamp == 0) {
                    //File doesn't exist before, no change detected.
                    return false;
                } else {
                    // File has been deleted. Reset the modified timestamp.
                    lastModifiedTimestamp = 0;
                    return true;
                }
            } else {
                long lastModified = targetFile.lastModified();
                if (lastModified != lastModifiedTimestamp) {
                    // File modified timestamp changed. Need to read the file content.
                    try (FileInputStream fileInputStream = new FileInputStream(targetFile)) {
                        byte[] b = new byte[1024];
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        int c;
                        while ((c = fileInputStream.read(b)) != -1) {
                            os.write(b, 0, c);
                        }
                        byte[] newContent = os.toByteArray();
                        if (!Arrays.equals(newContent, fileContent)) {
                            fileContent = newContent;
                            return true;
                        } else {
                            return false;
                        }
                    } catch (FileNotFoundException e) {
                        log.error("The existed file turns to missing, watch file=" + filePath, e);
                    } catch (IOException e) {
                        log.error("Read file failure, watch file=" + filePath, e);
                    } finally {
                        lastModifiedTimestamp = lastModified;
                    }
                }
                return false;
            }
        }
    }

    private static class NoopWatchedFile extends WatchedFile {
        public NoopWatchedFile() {
            super(null);
        }

        /**
         * @return false, as an noop file never changes.
         */
        @Override
        boolean detectContentChanged() {
            return false;
        }
    }
}
