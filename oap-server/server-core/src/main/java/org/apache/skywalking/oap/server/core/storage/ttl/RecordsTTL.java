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

package org.apache.skywalking.oap.server.core.storage.ttl;

import lombok.Data;

/**
 * RecordsTTL includes the definition of the TTL of the records data in the storage,
 * Records include traces, logs, sampled slow SQL statements, HTTP requests(by Rover), alarms, etc.
 * Super dataset of records are traces and logs, which volume should be much larger.
 */
@Data
public class RecordsTTL {
    private final int normal;
    private final int trace;
    private final int zipkinTrace;
    private final int log;
    private final int browserErrorLog;

    private int coldNormal = -1;
    private int coldTrace = -1;
    private int coldZipkinTrace = -1;
    private int coldLog = -1;
    private int coldBrowserErrorLog = -1;
}
