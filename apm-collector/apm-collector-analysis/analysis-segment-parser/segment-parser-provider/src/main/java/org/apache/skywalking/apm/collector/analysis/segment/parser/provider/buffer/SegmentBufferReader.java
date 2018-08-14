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

import com.google.protobuf.CodedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParseService;
import org.apache.skywalking.apm.collector.analysis.segment.parser.provider.parser.SegmentParse;
import org.apache.skywalking.apm.collector.analysis.segment.parser.provider.parser.SegmentParserListenerManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.network.proto.UpstreamSegment;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public enum SegmentBufferReader {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(SegmentBufferReader.class);
    private InputStream inputStream;
    private ModuleManager moduleManager;
    private SegmentParserListenerManager listenerManager;

    public void initialize(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            new RunnableWithExceptionProtection(this::preRead,
                t -> logger.error("Segment buffer pre read failure.", t)), 3, 3, TimeUnit.SECONDS);
    }

    public void setSegmentParserListenerManager(SegmentParserListenerManager listenerManager) {
        this.listenerManager = listenerManager;
    }

    private void preRead() {
        String readFileName = OffsetManager.INSTANCE.getReadFileName();
        if (StringUtils.isNotEmpty(readFileName)) {
            File readFile = new File(BufferFileConfig.BUFFER_PATH + readFileName);
            if (readFile.exists()) {
                deleteTheDataFilesBeforeReadFile(readFileName);
                long readFileOffset = OffsetManager.INSTANCE.getReadFileOffset();
                read(readFile, readFileOffset);
                readEarliestCreateDataFile();
            } else {
                deleteTheDataFilesBeforeReadFile(readFileName);
                readEarliestCreateDataFile();
            }
        } else {
            readEarliestCreateDataFile();
        }
    }

    private void deleteTheDataFilesBeforeReadFile(String readFileName) {
        File[] dataFiles = new File(BufferFileConfig.BUFFER_PATH).listFiles(new PrefixFileNameFilter());

        long readFileCreateTime = getFileCreateTime(readFileName);
        for (File dataFile : dataFiles) {
            long fileCreateTime = getFileCreateTime(dataFile.getName());
            if (fileCreateTime < readFileCreateTime) {
                dataFile.delete();
            } else if (fileCreateTime == readFileCreateTime) {
                break;
            }
        }
    }

    private long getFileCreateTime(String fileName) {
        fileName = fileName.replace(SegmentBufferManager.DATA_FILE_PREFIX + "_", Const.EMPTY_STRING);
        fileName = fileName.replace("." + Const.FILE_SUFFIX, Const.EMPTY_STRING);
        return Long.valueOf(fileName);
    }

    private void readEarliestCreateDataFile() {
        String readFileName = OffsetManager.INSTANCE.getReadFileName();
        File[] dataFiles = new File(BufferFileConfig.BUFFER_PATH).listFiles(new PrefixFileNameFilter());

        if (CollectionUtils.isNotEmpty(dataFiles)) {
            if (dataFiles[0].getName().equals(readFileName)) {
                return;
            }
        }

        for (File dataFile : dataFiles) {
            if (logger.isDebugEnabled()) {
                logger.debug("Reading segment buffer data file, file name: {}", dataFile.getAbsolutePath());
            }

            OffsetManager.INSTANCE.setReadOffset(dataFile.getName(), 0);
            if (!read(dataFile, 0)) {
                break;
            }
        }
    }

    private boolean read(File readFile, long readFileOffset) {
        try {
            inputStream = new FileInputStream(readFile);
            inputStream.skip(readFileOffset);

            String writeFileName = OffsetManager.INSTANCE.getWriteFileName();
            long endPoint = readFile.length();
            if (writeFileName.equals(readFile.getName())) {
                endPoint = OffsetManager.INSTANCE.getWriteFileOffset();
            }

            while (readFile.length() > readFileOffset && readFileOffset < endPoint) {
                UpstreamSegment upstreamSegment = UpstreamSegment.parser().parseDelimitedFrom(inputStream);
                SegmentParse parse = new SegmentParse(moduleManager, listenerManager);
                if (!parse.parse(upstreamSegment, ISegmentParseService.Source.Buffer)) {
                    return false;
                }

                final int serialized = upstreamSegment.getSerializedSize();
                readFileOffset = readFileOffset + CodedOutputStream.computeUInt32SizeNoTag(serialized) + serialized;

                if (logger.isDebugEnabled()) {
                    logger.debug("read segment buffer from file: {}, offset: {}, file length: {}", readFile.getName(), readFileOffset, readFile.length());
                }

                OffsetManager.INSTANCE.setReadOffset(readFileOffset);
            }

            inputStream.close();
            if (!writeFileName.equals(readFile.getName())) {
                readFile.delete();
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return true;
    }

    class PrefixFileNameFilter implements FilenameFilter {
        @Override public boolean accept(File dir, String name) {
            return name.startsWith(SegmentBufferManager.DATA_FILE_PREFIX);
        }
    }
}
