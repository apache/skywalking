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

import lombok.*;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * @author peng-yongsheng
 */
class Offset {

    private static final String SPLIT_CHARACTER = ",";
    @Getter private final ReadOffset readOffset;
    @Getter private final WriteOffset writeOffset;

    Offset() {
        writeOffset = new WriteOffset();
        readOffset = new ReadOffset(writeOffset);
    }

    String serialize() {
        return readOffset.getFileName() + SPLIT_CHARACTER + String.valueOf(readOffset.getOffset())
            + SPLIT_CHARACTER + writeOffset.getFileName() + SPLIT_CHARACTER + String.valueOf(writeOffset.getOffset());
    }

    void deserialize(String value) {
        if (!StringUtil.isEmpty(value)) {
            String[] values = value.split(SPLIT_CHARACTER);
            if (values.length == 4) {
                readOffset.setFileName(values[0]);
                readOffset.setOffset(Long.parseLong(values[1]));
                writeOffset.setFileName(values[2]);
                writeOffset.setOffset(Long.parseLong(values[3]));
            }
        }
    }

    static class ReadOffset {
        @Getter @Setter private String fileName;
        @Getter @Setter private long offset = 0;
        private final WriteOffset writeOffset;

        private ReadOffset(WriteOffset writeOffset) {
            this.writeOffset = writeOffset;
        }

        boolean isCurrentWriteFile() {
            return fileName.equals(writeOffset.fileName);
        }
    }

    static class WriteOffset {
        @Getter @Setter private String fileName;
        @Getter @Setter private long offset = 0;
    }
}
