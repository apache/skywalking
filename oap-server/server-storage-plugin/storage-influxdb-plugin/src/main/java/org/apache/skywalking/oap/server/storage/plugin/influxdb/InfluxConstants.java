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

package org.apache.skywalking.oap.server.storage.plugin.influxdb;

public interface InfluxConstants {
    String ID_COLUMN = "id";

    String NAME = "\"name\"";

    String ALL_FIELDS = "*::field";

    String SORT_DES = "top";

    String SORT_ASC = "bottom";

    String DURATION = "\"" + "duration" + "\"";

    interface TagName {

        String ID_COLUMN = "_id";

        String NAME = "_name_";

        String ENTITY_ID = "_entity_id";

        String TIME_BUCKET = "_time_bucket";

        String NODE_TYPE = "_node_type";

        String SERVICE_GROUP = "_service_group";

        String SERVICE_ID = "_service_id";
    }
}
