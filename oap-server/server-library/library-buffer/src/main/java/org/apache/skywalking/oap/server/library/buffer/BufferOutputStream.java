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

import java.io.*;
import org.apache.commons.io.FileUtils;

/**
 * @author peng-yongsheng
 */
public class BufferOutputStream extends OutputStream {

    private FileOutputStream outputStream;
    private final OffsetStream offsetStream;
    private final int dataFileMaxSize;
    private final CallBack callBack;

    BufferOutputStream(FileOutputStream outputStream, OffsetStream offsetStream, int dataFileMaxSize,
        CallBack callBack) {
        this.outputStream = outputStream;
        this.offsetStream = offsetStream;
        this.dataFileMaxSize = dataFileMaxSize;
        this.callBack = callBack;
    }

    @Override public void write(int b) throws IOException {
        outputStream.write(b);
        offsetStream.getOffset().getWriteOffset().setOffset(outputStream.getChannel().position());
        newOutputIfFull();
    }

    @Override public void write(byte[] b) throws IOException {
        outputStream.write(b);
        offsetStream.getOffset().getWriteOffset().setOffset(outputStream.getChannel().position());
        newOutputIfFull();
    }

    @Override public void write(byte[] b, int off, int len) throws IOException {
        outputStream.write(b, off, len);
        offsetStream.getOffset().getWriteOffset().setOffset(outputStream.getChannel().position());
        newOutputIfFull();
    }

    @Override public synchronized void flush() throws IOException {
        offsetStream.flush();
        outputStream.flush();
    }

    @Override public void close() throws IOException {
        outputStream.close();
    }

    private void newOutputIfFull() throws IOException {
        if (outputStream.getChannel().position() >= FileUtils.ONE_MB * dataFileMaxSize) {
            flush();
            outputStream = callBack.create();
        }
    }

    interface CallBack {
        FileOutputStream create() throws IOException;
    }
}
