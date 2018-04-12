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

package org.apache.skywalking.apm.collector.analysis.segment.parser.provider.buffer;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.FileUtils;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public enum OffsetManager {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(OffsetManager.class);

    private static final String OFFSET_FILE_PREFIX = "offset";
    private File offsetFile;
    private Offset offset;
    private boolean initialized = false;
    private RandomAccessFile randomAccessFile = null;
    private String lastOffsetRecord = Const.EMPTY_STRING;

    public synchronized void initialize() throws IOException {
        if (!initialized) {
            this.offset = new Offset();
            File dataPath = new File(BufferFileConfig.BUFFER_PATH);
            if (dataPath.mkdirs()) {
                createOffsetFile();
            } else {
                File[] offsetFiles = dataPath.listFiles(new PrefixFileNameFilter());
                if (CollectionUtils.isNotEmpty(offsetFiles) && offsetFiles.length > 0) {
                    for (int i = 0; i < offsetFiles.length; i++) {
                        if (i != offsetFiles.length - 1) {
                            offsetFiles[i].delete();
                        } else {
                            offsetFile = offsetFiles[i];
                        }
                    }
                } else {
                    createOffsetFile();
                }
            }
            String offsetRecord = FileUtils.INSTANCE.readLastLine(offsetFile);
            offset.deserialize(offsetRecord);
            initialized = true;

            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                new RunnableWithExceptionProtection(this::flush,
                    t -> logger.error("flush offset file in background failure.", t)
                ), 10, 3, TimeUnit.SECONDS);
        }
    }

    private void createOffsetFile() throws IOException {
        String timeBucket = String.valueOf(TimeBucketUtils.INSTANCE.getSecondTimeBucket(System.currentTimeMillis()));
        String offsetFileName = OFFSET_FILE_PREFIX + "_" + timeBucket + "." + Const.FILE_SUFFIX;
        offsetFile = new File(BufferFileConfig.BUFFER_PATH + offsetFileName);
        this.offset.getWriteOffset().setWriteFileName(Const.EMPTY_STRING);
        this.offset.getWriteOffset().setWriteFileOffset(0);
        this.offset.getReadOffset().setReadFileName(Const.EMPTY_STRING);
        this.offset.getReadOffset().setReadFileOffset(0);
        this.flush();
    }

    public void flush() {
        String offsetRecord = offset.serialize();
        if (!lastOffsetRecord.equals(offsetRecord)) {
            if (offsetFile.length() >= BufferFileConfig.BUFFER_OFFSET_MAX_FILE_SIZE) {
                nextFile();
            }
            FileUtils.INSTANCE.writeAppendToLast(offsetFile, randomAccessFile, offsetRecord);
            lastOffsetRecord = offsetRecord;
        }
    }

    private void nextFile() {
        String timeBucket = String.valueOf(TimeBucketUtils.INSTANCE.getSecondTimeBucket(System.currentTimeMillis()));
        String offsetFileName = OFFSET_FILE_PREFIX + "_" + timeBucket + "." + Const.FILE_SUFFIX;
        File newOffsetFile = new File(BufferFileConfig.BUFFER_PATH + offsetFileName);
        offsetFile.delete();
        offsetFile = newOffsetFile;
        this.flush();
    }

    public String getReadFileName() {
        return offset.getReadOffset().getReadFileName();
    }

    public long getReadFileOffset() {
        return offset.getReadOffset().getReadFileOffset();
    }

    public void setReadOffset(long readFileOffset) {
        offset.getReadOffset().setReadFileOffset(readFileOffset);
    }

    public void setReadOffset(String readFileName, long readFileOffset) {
        offset.getReadOffset().setReadFileName(readFileName);
        offset.getReadOffset().setReadFileOffset(readFileOffset);
    }

    public String getWriteFileName() {
        return offset.getWriteOffset().getWriteFileName();
    }

    public long getWriteFileOffset() {
        return offset.getWriteOffset().getWriteFileOffset();
    }

    public void setWriteOffset(String writeFileName, long writeFileOffset) {
        offset.getWriteOffset().setWriteFileName(writeFileName);
        offset.getWriteOffset().setWriteFileOffset(writeFileOffset);
    }

    public void setWriteOffset(long writeFileOffset) {
        offset.getWriteOffset().setWriteFileOffset(writeFileOffset);
    }

    class PrefixFileNameFilter implements FilenameFilter {
        @Override public boolean accept(File dir, String name) {
            return name.startsWith(OFFSET_FILE_PREFIX);
        }
    }
}
