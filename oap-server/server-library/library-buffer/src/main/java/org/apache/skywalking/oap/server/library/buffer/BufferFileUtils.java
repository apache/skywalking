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

import java.io.File;
import java.text.*;
import java.util.Date;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class BufferFileUtils {

    private static final Logger logger = LoggerFactory.getLogger(BufferFileUtils.class);

    private BufferFileUtils() {
    }

    static final String CHARSET = "UTF-8";
    static final String DATA_FILE_PREFIX = "data";
    static final String OFFSET_FILE_PREFIX = "offset";
    private static final String SEPARATOR = "-";
    private static final String SUFFIX = ".sw";
    private static final String DATA_FORMAT_STR = "yyyy-MM-dd";

    static String buildFileName(File directory, String prefix) {
        DateFormat dateFormat = new SimpleDateFormat(DATA_FORMAT_STR);
        return prefix + SEPARATOR + dateFormat.format(new Date()) + SEPARATOR + maxIndex(directory, prefix) + SUFFIX;
    }

    private static int maxIndex(File directory, String prefix) {
        String[] fileNames = directory.list(new PrefixFileFilter(prefix));

        int index = 0;
        if (fileNames != null) {
            for (String fileName : fileNames) {
                logger.debug("The file named {} with prefix {}", fileName, prefix);
                if (fileName.endsWith(SUFFIX)) {
                    try {
                        String subStr = fileName.substring(prefix.length() + SEPARATOR.length() + DATA_FORMAT_STR.length() + SEPARATOR.length());
                        String indexStr = subStr.substring(0, subStr.length() - SUFFIX.length());
                        index = index < Integer.valueOf(indexStr) ? Integer.valueOf(indexStr) : index;
                    } catch (Throwable t) {
                        logger.error("Get the max index id failure.", t);
                    }
                }
            }
        }
        return index + 1;
    }
}
