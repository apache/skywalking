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

package org.apache.skywalking.apm.collector.analysis.segment.parser.provider;

import org.apache.skywalking.apm.collector.core.module.ModuleConfig;

/**
 * @author peng-yongsheng
 */
public class AnalysisSegmentParserModuleConfig extends ModuleConfig {

    private String bufferFilePath;
    private String bufferOffsetMaxFileSize;
    private String bufferSegmentMaxFileSize;
    private boolean bufferFileCleanWhenRestart;

    public String getBufferFilePath() {
        return bufferFilePath;
    }

    public void setBufferFilePath(String bufferFilePath) {
        this.bufferFilePath = bufferFilePath;
    }

    public String getBufferOffsetMaxFileSize() {
        return bufferOffsetMaxFileSize;
    }

    public void setBufferOffsetMaxFileSize(String bufferOffsetMaxFileSize) {
        this.bufferOffsetMaxFileSize = bufferOffsetMaxFileSize;
    }

    public String getBufferSegmentMaxFileSize() {
        return bufferSegmentMaxFileSize;
    }

    public void setBufferSegmentMaxFileSize(String bufferSegmentMaxFileSize) {
        this.bufferSegmentMaxFileSize = bufferSegmentMaxFileSize;
    }

    public boolean isBufferFileCleanWhenRestart() {
        return bufferFileCleanWhenRestart;
    }

    public void setBufferFileCleanWhenRestart(boolean bufferFileCleanWhenRestart) {
        this.bufferFileCleanWhenRestart = bufferFileCleanWhenRestart;
    }
}
