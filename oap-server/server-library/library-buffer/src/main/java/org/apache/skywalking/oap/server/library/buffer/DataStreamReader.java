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

package org.apache.skywalking.oap.server.library.buffer;

import com.google.protobuf.*;
import java.io.*;
import java.util.Objects;
import java.util.concurrent.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.skywalking.apm.util.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class DataStreamReader<MESSAGE_TYPE extends GeneratedMessageV3> {

    private static final Logger logger = LoggerFactory.getLogger(DataStreamReader.class);

    private final File directory;
    private final Offset.ReadOffset readOffset;
    private final Parser<MESSAGE_TYPE> parser;
    private final CallBack<MESSAGE_TYPE> callBack;
    private File readingFile;
    private InputStream inputStream;

    DataStreamReader(File directory, Offset.ReadOffset readOffset, Parser<MESSAGE_TYPE> parser,
        CallBack<MESSAGE_TYPE> callBack) {
        this.directory = directory;
        this.readOffset = readOffset;
        this.parser = parser;
        this.callBack = callBack;
    }

    void initialize() {
        preRead();

        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(
            new RunnableWithExceptionProtection(this::read,
                t -> logger.error("Buffer data pre read failure.", t)), 3, 1, TimeUnit.SECONDS);
    }

    private void preRead() {
        String fileName = readOffset.getFileName();
        if (StringUtil.isEmpty(fileName)) {
            openInputStream(readEarliestDataFile());
        } else {
            File readingFile = new File(directory, fileName);
            if (readingFile.exists()) {
                openInputStream(readingFile);
                try {
                    inputStream.skip(readOffset.getOffset());
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            } else {
                openInputStream(readEarliestDataFile());
            }
        }
    }

    private void openInputStream(File readingFile) {
        try {
            this.readingFile = readingFile;
            if (Objects.nonNull(inputStream)) {
                inputStream.close();
            }

            inputStream = new FileInputStream(readingFile);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private File readEarliestDataFile() {
        String[] fileNames = directory.list(new PrefixFileFilter(BufferFileUtils.DATA_FILE_PREFIX));

        if (fileNames != null && fileNames.length > 0) {
            BufferFileUtils.sort(fileNames);
            readOffset.setFileName(fileNames[0]);
            readOffset.setOffset(0);
            return new File(directory, fileNames[0]);
        } else {
            return null;
        }
    }

    private void read() {
        if (logger.isDebugEnabled()) {
            logger.debug("Read buffer data");
        }

        try {
            if (readOffset.getOffset() == readingFile.length() && !readOffset.isCurrentWriteFile()) {
                FileUtils.forceDelete(readingFile);
                openInputStream(readEarliestDataFile());
            }

            while (readOffset.getOffset() < readingFile.length()) {

                MESSAGE_TYPE messageType = parser.parseDelimitedFrom(inputStream);
                if (messageType != null) {
                    int i = 0;
                    while (!callBack.call(messageType)) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(500);
                        } catch (InterruptedException e) {
                            logger.error(e.getMessage());
                        }

                        i++;
                        if (i == 10) {
                            break;
                        }
                    }
                    final int serialized = messageType.getSerializedSize();
                    final int offset = CodedOutputStream.computeUInt32SizeNoTag(serialized) + serialized;
                    readOffset.setOffset(readOffset.getOffset() + offset);
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public interface CallBack<MESSAGE_TYPE extends GeneratedMessageV3> {
        boolean call(MESSAGE_TYPE message);
    }
}
