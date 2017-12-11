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


package org.apache.skywalking.apm.collector.storage.table.application;

import org.apache.skywalking.apm.collector.storage.table.CommonMetricTable;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetricTable extends CommonMetricTable {
    public static final String TABLE = "application_reference";
    public static final String COLUMN_FRONT_APPLICATION_ID = "front_application_id";
    public static final String COLUMN_BEHIND_APPLICATION_ID = "behind_application_id";
    public static final String COLUMN_SATISFIED_COUNT = "satisfied_count";
    public static final String COLUMN_TOLERATING_COUNT = "tolerating_count";
    public static final String COLUMN_FRUSTRATED_COUNT = "frustrated_count";
}
