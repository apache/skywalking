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

/**
 * @author peng-yongsheng
 */
public class Offset {

    private static final String SPLIT_CHARACTER = ",";
    private ReadOffset readOffset;
    private WriteOffset writeOffset;

    public Offset() {
        readOffset = new ReadOffset();
        writeOffset = new WriteOffset();
    }

    public String serialize() {
        return readOffset.getReadFileName() + SPLIT_CHARACTER + String.valueOf(readOffset.getReadFileOffset())
            + SPLIT_CHARACTER + writeOffset.getWriteFileName() + SPLIT_CHARACTER + String.valueOf(writeOffset.getWriteFileOffset());
    }

    public void deserialize(String value) {
        String[] values = value.split(SPLIT_CHARACTER);
        if (values.length == 4) {
            this.readOffset.readFileName = values[0];
            this.readOffset.readFileOffset = Long.parseLong(values[1]);
            this.writeOffset.writeFileName = values[2];
            this.writeOffset.writeFileOffset = Long.parseLong(values[3]);
        }
    }

    public ReadOffset getReadOffset() {
        return readOffset;
    }

    public WriteOffset getWriteOffset() {
        return writeOffset;
    }

    public static class ReadOffset {
        private String readFileName;
        private long readFileOffset = 0;

        public String getReadFileName() {
            return readFileName;
        }

        public long getReadFileOffset() {
            return readFileOffset;
        }

        public void setReadFileName(String readFileName) {
            this.readFileName = readFileName;
        }

        public void setReadFileOffset(long readFileOffset) {
            this.readFileOffset = readFileOffset;
        }
    }

    public static class WriteOffset {
        private String writeFileName;
        private long writeFileOffset = 0;

        public String getWriteFileName() {
            return writeFileName;
        }

        public long getWriteFileOffset() {
            return writeFileOffset;
        }

        public void setWriteFileName(String writeFileName) {
            this.writeFileName = writeFileName;
        }

        public void setWriteFileOffset(long writeFileOffset) {
            this.writeFileOffset = writeFileOffset;
        }
    }
}
