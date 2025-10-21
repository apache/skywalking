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

package org.apache.skywalking.oap.server.core.query.type;

import java.util.HashMap;
import java.util.Map;

public enum PprofTaskLogOperationType {
    NOTIFIED(1), // when sniffer has execution finished to report
    EXECUTION_FINISHED(2), // when sniffer has execution finished to report
    PPROF_UPLOAD_FILE_TOO_LARGE_ERROR(
        3), // when sniffer finished task but jfr file is to large that oap server can not receive
    EXECUTION_TASK_ERROR(4) // when sniffer fails to execute its task
    ;

    private final int code;
    private static final Map<Integer, PprofTaskLogOperationType> CACHE = new HashMap<Integer, PprofTaskLogOperationType>();

    static {
        for (PprofTaskLogOperationType val : PprofTaskLogOperationType.values()) {
            CACHE.put(val.getCode(), val);
        }
    }

    /**
     * Parse operation type by code
     */
    public static PprofTaskLogOperationType parse(int code) {
        return CACHE.get(code);
    }

    PprofTaskLogOperationType(int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }
}
