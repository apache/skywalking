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

package org.apache.skywalking.oap.server.core.analysis.manual.cache;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.TopNStreamProcessor;
import org.apache.skywalking.oap.server.core.source.VirtualCacheOperation;
import org.apache.skywalking.oap.server.core.source.CacheSlowAccess;

public class CacheSlowAccessDispatcher implements SourceDispatcher<CacheSlowAccess> {

    @Override
    public void dispatch(CacheSlowAccess source) {
        // There are only two kinds of Operation : write or read .Refer VirtualCacheProcessor#prepareVSIfNecessary
        if (source.getOperation() == VirtualCacheOperation.Read) {
            TopNCacheReadCommand readCommand = new TopNCacheReadCommand();
            readCommand.setId(source.getId());
            readCommand.setCommand(source.getCommand() + " " + source.getKey());
            readCommand.setLatency(source.getLatency());
            readCommand.setTraceId(source.getTraceId());
            readCommand.setEntityId(source.getCacheServiceId());
            readCommand.setTimeBucket(source.getTimeBucket());
            readCommand.setTimestamp(source.getTimestamp());
            TopNStreamProcessor.getInstance().in(readCommand);
        } else if (source.getOperation() == VirtualCacheOperation.Write) {
            TopNCacheWriteCommand writeCommand = new TopNCacheWriteCommand();
            writeCommand.setId(source.getId());
            writeCommand.setCommand(source.getCommand() + " " + source.getKey());
            writeCommand.setLatency(source.getLatency());
            writeCommand.setTraceId(source.getTraceId());
            writeCommand.setEntityId(source.getCacheServiceId());
            writeCommand.setTimeBucket(source.getTimeBucket());
            writeCommand.setTimestamp(source.getTimestamp());
            TopNStreamProcessor.getInstance().in(writeCommand);
        }
    }
}
