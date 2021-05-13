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

package org.apache.skywalking.oap.server.core.analysis.metrics;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;

public class MetricsMetaInfo {
    @Setter
    @Getter
    private String metricsName;
    @Setter
    @Getter
    private int scope;
    @Setter
    @Getter
    private String id;

    public MetricsMetaInfo(String metricsName, int scope) {
        this.metricsName = metricsName;
        this.scope = scope;
        this.id = Const.EMPTY_STRING;
    }

    public MetricsMetaInfo(String metricsName, int scope, String id) {
        this.metricsName = metricsName;
        this.scope = scope;
        this.id = id;
    }

    @Override
    public String toString() {
        return "MetricsMetaInfo{" + "metricsName='" + metricsName + '\'' + ", scope=" + scope + ", id='" + id + '\'' + '}';
    }
}
