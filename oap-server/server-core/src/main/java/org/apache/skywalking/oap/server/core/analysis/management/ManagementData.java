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

package org.apache.skywalking.oap.server.core.analysis.management;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * ManagementData provides the basic CRUD operations, but not time serious like {@link NoneStream}.
 */
public abstract class ManagementData implements StorageData {
    public static final String TIME_BUCKET = "time_bucket";

    /**
     * Time attribute, the data is time sensitive, but is required for time serious database
     */
    @Getter
    @Setter
    @Column(columnName = TIME_BUCKET)
    private long timeBucket;
}
