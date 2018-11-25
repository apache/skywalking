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
import org.apache.commons.io.FileUtils;
import org.apache.skywalking.apm.util.StringUtil;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class DataStreamWriter<MESSAGE_TYPE extends GeneratedMessageV3> {

    private static final Logger logger = LoggerFactory.getLogger(DataStreamWriter.class);

    private final File directory;
    private final Offset.WriteOffset writeOffset;

    private final int dataFileMaxSize;

    private boolean initialized = false;
    private FileOutputStream outputStream;

    DataStreamWriter(File directory, Offset.WriteOffset writeOffset, int dataFileMaxSize) {
        this.directory = directory;
        this.dataFileMaxSize = dataFileMaxSize;
        this.writeOffset = writeOffset;
    }

    synchronized void initialize() throws IOException {
        if (!initialized) {
            String writeFileName = writeOffset.getFileName();

            File writingFile;
            if (StringUtil.isEmpty(writeFileName)) {
                writingFile = createNewFile();
            } else {
                writingFile = new File(directory, writeFileName);
                if (!writingFile.exists()) {
                    writingFile = createNewFile();
                }
            }

            outputStream = FileUtils.openOutputStream(writingFile, true);
            initialized = true;
        }
    }

    private File createNewFile() throws IOException {
        String fileName = BufferFileUtils.buildFileName(BufferFileUtils.DATA_FILE_PREFIX);
        File writingFile = new File(directory, fileName);

        boolean created = writingFile.createNewFile();
        if (!created) {
            logger.info("The file named {} already exists.", writingFile.getAbsolutePath());
        } else {
            logger.info("Create a new buffer data file: {}", writingFile.getAbsolutePath());
        }

        writeOffset.setOffset(0);
        writeOffset.setFileName(writingFile.getName());

        return writingFile;
    }

    synchronized void write(AbstractMessageLite messageLite) {
        try {
            messageLite.writeDelimitedTo(outputStream);
            long position = outputStream.getChannel().position();
            writeOffset.setOffset(position);
            if (position >= (FileUtils.ONE_MB * dataFileMaxSize)) {
                File writingFile = createNewFile();
                outputStream.close();
                outputStream = FileUtils.openOutputStream(writingFile, true);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
