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


package org.apache.skywalking.apm.collector.storage.table.alerting;

import org.apache.skywalking.apm.collector.core.data.CommonTable;

/**
 * @author peng-yongsheng
 */
public class AlertingListTable extends CommonTable {
    public static final String TABLE = "alerting_list";
    public static final String COLUMN_LAYER = "layer";
    public static final String COLUMN_LAYER_ID = "layer_id";
    public static final String COLUMN_FIRST_TIME_BUCKET = "first_time_bucket";
    public static final String COLUMN_LAST_TIME_BUCKET = "last_time_bucket";
    public static final String COLUMN_EXPECTED = "expected";
    public static final String COLUMN_ACTUAL = "actual";
    public static final String COLUMN_VALID = "valid";
}
