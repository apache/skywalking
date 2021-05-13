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

package org.apache.skywalking.oap.server.tool.profile.exporter;

import lombok.Data;

import java.io.File;

@Data
public class ExporterConfig {

    // profile task id
    private String taskId;

    // profiled trace id
    private String traceId;

    // export to file path
    private String analyzeResultDist;

    /**
     * parse config from command line
     */
    public static ExporterConfig parse(String[] args) {
        if (args == null || args.length != 3) {
            throw new IllegalArgumentException("missing config, please recheck");
        }

        // build config
        ExporterConfig config = new ExporterConfig();
        config.setTaskId(args[0]);
        config.setTraceId(args[1]);
        config.setAnalyzeResultDist(args[2]);

        return config;
    }

    /**
     * initialize config, such as check dist path
     */
    public void init() {
        File dist = new File(analyzeResultDist);
        if (!dist.exists()) {
            dist.mkdirs();
            return;
        }

        if (dist.isFile()) {
            throw new IllegalArgumentException(analyzeResultDist + " must be a directory");
        }
    }
}
