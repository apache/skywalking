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

import java.util.Arrays;

/**
 * This class is a util for sort or build file name for the gRPC streaming data.
 * Sort the files by the created time in order to read the data file sequential.
 *
 * @author peng-yongsheng
 */
class BufferFileUtils {

    private BufferFileUtils() {
    }

    static final String CHARSET = "UTF-8";
    static final String DATA_FILE_PREFIX = "data";
    static final String OFFSET_FILE_PREFIX = "offset";
    private static final String SEPARATOR = "-";
    private static final String SUFFIX = ".sw";

    static void sort(String[] fileList) {
        Arrays.sort(fileList, (f1, f2) -> {
            long t1 = Long.parseLong(f1.substring(0, f1.length() - 3).split(SEPARATOR)[1]);
            long t2 = Long.parseLong(f2.substring(0, f2.length() - 3).split(SEPARATOR)[1]);

            return (int)(t1 - t2);
        });
    }

    static String buildFileName(String prefix) {
        return prefix + SEPARATOR + System.currentTimeMillis() + SUFFIX;
    }
}