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

/**
 * @author peng-yongsheng
 */
public class BufferInputStream extends InputStream {

    private FileInputStream inputStream;
    private final OffsetStream offsetStream;
    private final CallBack callBack;

    BufferInputStream(FileInputStream inputStream, OffsetStream offsetStream, CallBack callBack) {
        this.inputStream = inputStream;
        this.offsetStream = offsetStream;
        this.callBack = callBack;
    }

    @Override public int read(byte[] b) throws IOException {
        int read = inputStream.read(b);
        readNextDataFile();
        offsetStream.getOffset().getReadOffset().setOffset(inputStream.getChannel().position());
        return read;
    }

    @Override public int read(byte[] b, int off, int len) throws IOException {
        int read = inputStream.read(b, off, len);
        offsetStream.getOffset().getReadOffset().setOffset(inputStream.getChannel().position());
        return read;
    }

    @Override public int read() throws IOException {
        int read = inputStream.read();
        offsetStream.getOffset().getReadOffset().setOffset(inputStream.getChannel().position());
        return read;
    }

    @Override public long skip(long n) throws IOException {
        long skip = inputStream.skip(n);
        offsetStream.getOffset().getReadOffset().setOffset(inputStream.getChannel().position());
        return skip;
    }

    @Override public int available() throws IOException {
        int available = inputStream.available();
        offsetStream.getOffset().getReadOffset().setOffset(inputStream.getChannel().position());
        return available;
    }

    @Override public void close() throws IOException {
        inputStream.close();
    }

    @Override public synchronized void mark(int readlimit) {
        inputStream.mark(readlimit);
    }

    @Override public synchronized void reset() throws IOException {
        inputStream.reset();
        offsetStream.getOffset().getReadOffset().setOffset(inputStream.getChannel().position());
    }

    @Override public boolean markSupported() {
        return inputStream.markSupported();
    }

    private void readNextDataFile() {
        inputStream = callBack.onNext();
    }

    interface CallBack {
        FileInputStream onNext();
    }
}
