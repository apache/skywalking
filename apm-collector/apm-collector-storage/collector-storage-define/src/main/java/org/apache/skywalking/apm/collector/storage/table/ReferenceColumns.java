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

package org.apache.skywalking.apm.collector.storage.table;

import org.apache.skywalking.apm.collector.core.data.ColumnName;

/**
 * @author peng-yongsheng
 */
public interface ReferenceColumns {
    ColumnName FRONT_APPLICATION_ID = new ColumnName("front_application_id", "fai");

    ColumnName BEHIND_APPLICATION_ID = new ColumnName("behind_application_id", "bai");

    ColumnName FRONT_INSTANCE_ID = new ColumnName("front_instance_id", "fii");

    ColumnName BEHIND_INSTANCE_ID = new ColumnName("behind_instance_id", "bii");

    ColumnName FRONT_SERVICE_ID = new ColumnName("front_service_id", "fsi");

    ColumnName BEHIND_SERVICE_ID = new ColumnName("behind_service_id", "bsi");
}
