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

package org.apache.skywalking.oap.server.core.profiling.asyncprofiler.analyze;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.source.JFRProfilingData;
import org.apache.skywalking.oap.server.library.jfr.parser.convert.Arguments;
import org.apache.skywalking.oap.server.library.jfr.parser.convert.FrameTree;
import org.apache.skywalking.oap.server.library.jfr.parser.convert.JfrParser;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

@Slf4j
public class JfrAnalyzer {
    private final ModuleManager moduleManager;

    public JfrAnalyzer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public List<JFRProfilingData> parseJfr(String jfrFileName) throws IOException {
        List<JFRProfilingData> result = Lists.newArrayList();
        Arguments arguments = new Arguments();
        // TODO config cpu state
        arguments.lines = true;
        arguments.state = "default,runnable,sleeping";
        Map<JFREventType, FrameTree> event2treeMap = JfrParser.dumpTree(jfrFileName, arguments);
        for (Map.Entry<JFREventType, FrameTree> entry : event2treeMap.entrySet()) {
            JFREventType event = entry.getKey();
            FrameTree tree = entry.getValue();
            JFRProfilingData data = new JFRProfilingData();
            data.setEventType(event);
            data.setFrameTree(tree);
            result.add(data);
        }

        return result;
    }

    public List<JFRProfilingData> parseJfr(ByteBuffer buf) throws IOException {
        List<JFRProfilingData> result = Lists.newArrayList();
        Arguments arguments = new Arguments();
        // TODO config cpu state
        arguments.lines = true;
        arguments.state = "default,runnable,sleeping";
        Map<JFREventType, FrameTree> event2treeMap = JfrParser.dumpTree(buf, arguments);
        for (Map.Entry<JFREventType, FrameTree> entry : event2treeMap.entrySet()) {
            JFREventType event = entry.getKey();
            FrameTree tree = entry.getValue();
            JFRProfilingData data = new JFRProfilingData();
            data.setEventType(event);
            data.setFrameTree(tree);
            result.add(data);
        }

        return result;
    }

}
