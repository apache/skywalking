/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream;

import java.util.Map;
import org.skywalking.apm.collector.agentstream.config.BufferFileConfig;
import org.skywalking.apm.collector.core.config.GroupConfigParser;

/**
 * @author peng-yongsheng
 */
public class AgentStreamModuleGroupConfigParser implements GroupConfigParser {

    private static final String BUFFER_OFFSET_MAX_FILE_SIZE = "buffer_offset_max_file_size";
    private static final String BUFFER_SEGMENT_MAX_FILE_SIZE = "buffer_segment_max_file_size";

    @Override public void parse(Map<String, Map> config) {
        if (config.containsKey(GroupConfigParser.NODE_NAME)) {
            Map<String, String> groupConfig = config.get(GroupConfigParser.NODE_NAME);

            if (groupConfig.containsKey(BUFFER_OFFSET_MAX_FILE_SIZE)) {
                String sizeStr = groupConfig.get(BUFFER_OFFSET_MAX_FILE_SIZE).toUpperCase();
                if (sizeStr.endsWith("K")) {
                    int size = Integer.parseInt(sizeStr.replace("K", ""));
                    BufferFileConfig.BUFFER_OFFSET_MAX_FILE_SIZE = size * 1024;
                } else if (sizeStr.endsWith("KB")) {
                    int size = Integer.parseInt(sizeStr.replace("KB", ""));
                    BufferFileConfig.BUFFER_OFFSET_MAX_FILE_SIZE = size * 1024;
                } else if (sizeStr.endsWith("M")) {
                    int size = Integer.parseInt(sizeStr.replace("M", ""));
                    BufferFileConfig.BUFFER_OFFSET_MAX_FILE_SIZE = size * 1024 * 1024;
                } else if (sizeStr.endsWith("MB")) {
                    int size = Integer.parseInt(sizeStr.replace("MB", ""));
                    BufferFileConfig.BUFFER_OFFSET_MAX_FILE_SIZE = size * 1024 * 1024;
                } else {
                    BufferFileConfig.BUFFER_OFFSET_MAX_FILE_SIZE = 10 * 1024 * 1024;
                }
            } else {
                BufferFileConfig.BUFFER_OFFSET_MAX_FILE_SIZE = 10 * 1024 * 1024;
            }

            if (groupConfig.containsKey(BUFFER_SEGMENT_MAX_FILE_SIZE)) {
                String sizeStr = groupConfig.get(BUFFER_SEGMENT_MAX_FILE_SIZE).toUpperCase();
                if (sizeStr.endsWith("K")) {
                    int size = Integer.parseInt(sizeStr.replace("K", ""));
                    BufferFileConfig.BUFFER_SEGMENT_MAX_FILE_SIZE = size * 1024;
                } else if (sizeStr.endsWith("KB")) {
                    int size = Integer.parseInt(sizeStr.replace("KB", ""));
                    BufferFileConfig.BUFFER_SEGMENT_MAX_FILE_SIZE = size * 1024;
                } else if (sizeStr.endsWith("M")) {
                    int size = Integer.parseInt(sizeStr.replace("M", ""));
                    BufferFileConfig.BUFFER_SEGMENT_MAX_FILE_SIZE = size * 1024 * 1024;
                } else if (sizeStr.endsWith("MB")) {
                    int size = Integer.parseInt(sizeStr.replace("MB", ""));
                    BufferFileConfig.BUFFER_SEGMENT_MAX_FILE_SIZE = size * 1024 * 1024;
                } else {
                    BufferFileConfig.BUFFER_SEGMENT_MAX_FILE_SIZE = 1024 * 1024;
                }
            } else {
                BufferFileConfig.BUFFER_SEGMENT_MAX_FILE_SIZE = 1024 * 1024;
            }
        }
    }
}
