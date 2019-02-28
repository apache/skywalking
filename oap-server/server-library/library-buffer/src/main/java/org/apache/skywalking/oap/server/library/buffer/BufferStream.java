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
import java.nio.channels.FileLock;
import org.apache.commons.io.FileUtils;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class BufferStream<MESSAGE_TYPE extends GeneratedMessageV3> {

    private static final Logger logger = LoggerFactory.getLogger(BufferStream.class);

    private final String absolutePath;
    private final boolean cleanWhenRestart;
    private final int dataFileMaxSize;
    private final int offsetFileMaxSize;
    private final Parser<MESSAGE_TYPE> parser;
    private final DataStreamReader.CallBack<MESSAGE_TYPE> callBack;
    private DataStream<MESSAGE_TYPE> dataStream;

    private BufferStream(String absolutePath, boolean cleanWhenRestart, int dataFileMaxSize, int offsetFileMaxSize,
        Parser<MESSAGE_TYPE> parser, DataStreamReader.CallBack<MESSAGE_TYPE> callBack) {
        this.absolutePath = absolutePath;
        this.cleanWhenRestart = cleanWhenRestart;
        this.dataFileMaxSize = dataFileMaxSize;
        this.offsetFileMaxSize = offsetFileMaxSize;
        this.parser = parser;
        this.callBack = callBack;
    }

    public synchronized void initialize() throws IOException {
        File directory = new File(absolutePath);
        FileUtils.forceMkdir(directory);
        tryLock(directory);

        dataStream = new DataStream<>(directory, dataFileMaxSize, offsetFileMaxSize, parser, callBack);

        if (cleanWhenRestart) {
            dataStream.clean();
        }

        dataStream.initialize();
    }

    public synchronized void write(AbstractMessageLite messageLite) {
        dataStream.getWriter().write(messageLite);
    }

    private void tryLock(File directory) {
        logger.info("Try to lock buffer directory, directory is: " + directory.getAbsolutePath());
        FileLock lock = null;

        try {
            lock = new FileOutputStream(new File(directory, "lock")).getChannel().tryLock();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        if (lock == null) {
            throw new RuntimeException("The buffer directory is reading or writing by another thread, directory is: " + directory.getAbsolutePath());
        }

        logger.info("Lock buffer directory successfully, directory is: " + directory.getAbsolutePath());
    }

    public static class Builder<MESSAGE_TYPE extends GeneratedMessageV3> {

        private final String absolutePath;
        private boolean cleanWhenRestart;
        private int dataFileMaxSize;
        private int offsetFileMaxSize;
        private Parser<MESSAGE_TYPE> parser;
        private DataStreamReader.CallBack<MESSAGE_TYPE> callBack;

        public Builder(String absolutePath) {
            this.absolutePath = absolutePath;
        }

        public BufferStream<MESSAGE_TYPE> build() {
            return new BufferStream<>(absolutePath, cleanWhenRestart, dataFileMaxSize, offsetFileMaxSize, parser, callBack);
        }

        public Builder<MESSAGE_TYPE> cleanWhenRestart(boolean cleanWhenRestart) {
            this.cleanWhenRestart = cleanWhenRestart;
            return this;
        }

        public Builder<MESSAGE_TYPE> offsetFileMaxSize(int offsetFileMaxSize) {
            this.offsetFileMaxSize = offsetFileMaxSize;
            return this;
        }

        public Builder<MESSAGE_TYPE> dataFileMaxSize(int dataFileMaxSize) {
            this.dataFileMaxSize = dataFileMaxSize;
            return this;
        }

        public Builder<MESSAGE_TYPE> parser(Parser<MESSAGE_TYPE> parser) {
            this.parser = parser;
            return this;
        }

        public Builder<MESSAGE_TYPE> callBack(
            DataStreamReader.CallBack<MESSAGE_TYPE> callBack) {
            this.callBack = callBack;
            return this;
        }
    }
}
