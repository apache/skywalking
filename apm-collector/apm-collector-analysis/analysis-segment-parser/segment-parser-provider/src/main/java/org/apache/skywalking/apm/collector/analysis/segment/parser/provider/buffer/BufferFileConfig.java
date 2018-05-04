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

import org.apache.skywalking.apm.collector.analysis.segment.parser.provider.AnalysisSegmentParserModuleConfig;
import org.apache.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author peng-yongsheng
 */
public class BufferFileConfig {
    static int BUFFER_OFFSET_MAX_FILE_SIZE = 10 * 1024 * 1024;
    static int BUFFER_SEGMENT_MAX_FILE_SIZE = 10 * 1024 * 1024;
    static String BUFFER_PATH = "../buffer/";
    static boolean BUFFER_FILE_CLEAN_WHEN_RESTART = false;

    public static class Parser {

        public void parse(AnalysisSegmentParserModuleConfig config) {
            if (StringUtils.isNotEmpty(config.getBufferFilePath())) {
                BUFFER_PATH = config.getBufferFilePath();
            }

            if (StringUtils.isNotEmpty(config.getBufferOffsetMaxFileSize())) {
                String sizeStr = config.getBufferOffsetMaxFileSize().toUpperCase();
                if (sizeStr.endsWith("K")) {
                    int size = Integer.parseInt(sizeStr.replace("K", ""));
                    BUFFER_OFFSET_MAX_FILE_SIZE = size * 1024;
                } else if (sizeStr.endsWith("KB")) {
                    int size = Integer.parseInt(sizeStr.replace("KB", ""));
                    BUFFER_OFFSET_MAX_FILE_SIZE = size * 1024;
                } else if (sizeStr.endsWith("M")) {
                    int size = Integer.parseInt(sizeStr.replace("M", ""));
                    BUFFER_OFFSET_MAX_FILE_SIZE = size * 1024 * 1024;
                } else if (sizeStr.endsWith("MB")) {
                    int size = Integer.parseInt(sizeStr.replace("MB", ""));
                    BUFFER_OFFSET_MAX_FILE_SIZE = size * 1024 * 1024;
                } else {
                    BUFFER_OFFSET_MAX_FILE_SIZE = 10 * 1024 * 1024;
                }
            } else {
                BUFFER_OFFSET_MAX_FILE_SIZE = 10 * 1024 * 1024;
            }

            if (StringUtils.isNotEmpty(config.getBufferSegmentMaxFileSize())) {
                String sizeStr = config.getBufferSegmentMaxFileSize().toUpperCase();
                if (sizeStr.endsWith("K")) {
                    int size = Integer.parseInt(sizeStr.replace("K", ""));
                    BUFFER_SEGMENT_MAX_FILE_SIZE = size * 1024;
                } else if (sizeStr.endsWith("KB")) {
                    int size = Integer.parseInt(sizeStr.replace("KB", ""));
                    BUFFER_SEGMENT_MAX_FILE_SIZE = size * 1024;
                } else if (sizeStr.endsWith("M")) {
                    int size = Integer.parseInt(sizeStr.replace("M", ""));
                    BUFFER_SEGMENT_MAX_FILE_SIZE = size * 1024 * 1024;
                } else if (sizeStr.endsWith("MB")) {
                    int size = Integer.parseInt(sizeStr.replace("MB", ""));
                    BUFFER_SEGMENT_MAX_FILE_SIZE = size * 1024 * 1024;
                } else {
                    BUFFER_SEGMENT_MAX_FILE_SIZE = 1024 * 1024;
                }
            } else {
                BUFFER_SEGMENT_MAX_FILE_SIZE = 1024 * 1024;
            }

            BUFFER_FILE_CLEAN_WHEN_RESTART = config.isBufferFileCleanWhenRestart();
        }
    }
}
