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

package org.apache.skywalking.oap.server.core.analysis.indicator;

import java.util.Map;
import lombok.*;
import org.apache.skywalking.oap.server.core.analysis.data.StreamData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * @author peng-yongsheng
 */
public abstract class Indicator extends StreamData {

    protected static final String TIME_BUCKET = "time_bucket";

    @Getter @Setter @Column(columnName = TIME_BUCKET) private long timeBucket;

    public abstract String id();

    public abstract void combine(Indicator indicator);

    public abstract String name();

    public abstract Map<String, Object> toMap();

    public abstract Indicator newOne(Map<String, Object> dbMap);
}
