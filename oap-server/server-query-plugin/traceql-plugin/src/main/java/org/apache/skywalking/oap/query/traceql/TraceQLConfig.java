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

package org.apache.skywalking.oap.query.traceql;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Setter
@Getter
public class TraceQLConfig extends ModuleConfig {
    private String restHost;
    private int restPort;
    private boolean enableDatasourceZipkin;
    private boolean enableDatasourceSkywalking;
    private String restContextPathZipkin;
    private String restContextPathSkywalking;
    private long restIdleTimeOut = 30000;
    private int restAcceptQueueSize = 0;
    private long lookback = 86400000L;
    private String zipkinTracesListResultTags = ZIPKIN_TRACES_LIST_RESULT_TAGS;
    private String skywalkingTracesListResultTags = SKYWALKING_TRACES_LIST_RESULT_TAGS;

    private static final String ZIPKIN_TRACES_LIST_RESULT_TAGS = String.join(
        Const.COMMA,
        "http.method"
    );
    private static final String SKYWALKING_TRACES_LIST_RESULT_TAGS = String.join(
        Const.COMMA,
        "http.method",
        "http.status_code",
        "rpc.status_code",
        "db.type",
        "db.instance",
        "mq.queue",
        "mq.topic",
        "mq.broker"
    );
}
