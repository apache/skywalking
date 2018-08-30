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
import java.util.concurrent.*;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.skywalking.apm.util.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class DataStreamReader<MESSAGE_TYPE extends GeneratedMessageV3> {

    private static final Logger logger = LoggerFactory.getLogger(DataStreamReader.class);

    private final File directory;
    private final Offset.ReadOffset readOffset;
    private final Parser<MESSAGE_TYPE> parser;
    private final CallBack<MESSAGE_TYPE> callBack;
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

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            new RunnableWithExceptionProtection(this::read,
                t -> logger.error("Segment buffer pre read failure.", t)), 3, 3, TimeUnit.SECONDS);
    }

    private void preRead() {
        String fileName = readOffset.getFileName();
        if (StringUtil.isEmpty(fileName)) {
            openInputStream(readEarliestCreateDataFile());
        } else {
            File dataFile = new File(directory, fileName);
            if (dataFile.exists()) {
                openInputStream(dataFile);
                read();
            } else {
                openInputStream(readEarliestCreateDataFile());
            }
        }
    }

    private void openInputStream(File readFile) {
        try {
            inputStream = new FileInputStream(readFile);
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private File readEarliestCreateDataFile() {
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
        try {
            MESSAGE_TYPE messageType = parser.parseDelimitedFrom(inputStream);
            if (messageType != null) {
                callBack.call(messageType);
                final int serialized = messageType.getSerializedSize();
                final int offset = CodedOutputStream.computeUInt32SizeNoTag(serialized) + serialized;
                readOffset.setOffset(readOffset.getOffset() + offset);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    interface CallBack<MESSAGE_TYPE extends GeneratedMessageV3> {
        void call(MESSAGE_TYPE message);
    }
}