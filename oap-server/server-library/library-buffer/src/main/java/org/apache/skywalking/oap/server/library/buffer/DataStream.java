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
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class DataStream<MESSAGE_TYPE extends GeneratedMessageV3> {

    private static final Logger logger = LoggerFactory.getLogger(DataStream.class);

    private final File directory;
    private final OffsetStream offsetStream;
    @Getter private final DataStreamReader<MESSAGE_TYPE> reader;
    @Getter private final DataStreamWriter<MESSAGE_TYPE> writer;
    private boolean initialized = false;

    DataStream(File directory, int dataFileMaxSize, int offsetFileMaxSize, Parser<MESSAGE_TYPE> parser,
        DataStreamReader.CallBack<MESSAGE_TYPE> callBack) {
        this.directory = directory;
        this.offsetStream = new OffsetStream(directory, offsetFileMaxSize);
        this.writer = new DataStreamWriter<>(directory, offsetStream.getOffset().getWriteOffset(), dataFileMaxSize);
        this.reader = new DataStreamReader<>(directory, offsetStream.getOffset().getReadOffset(), parser, callBack);
    }

    void clean() throws IOException {
        String[] fileNames = directory.list(new PrefixFileFilter(BufferFileUtils.DATA_FILE_PREFIX));
        if (fileNames != null) {
            for (String fileName : fileNames) {
                File file = new File(directory, fileName);
                if (logger.isDebugEnabled()) {
                    logger.debug("Delete buffer data file: {}", file.getAbsolutePath());
                }
                FileUtils.forceDelete(file);
            }
        }

        offsetStream.clean();
    }

    synchronized void initialize() throws IOException {
        if (!initialized) {
            offsetStream.initialize();
            writer.initialize();
            reader.initialize();
            initialized = true;
        }
    }
}
