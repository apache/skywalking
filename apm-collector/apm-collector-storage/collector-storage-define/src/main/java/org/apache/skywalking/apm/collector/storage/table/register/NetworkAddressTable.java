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

package org.apache.skywalking.apm.collector.storage.table.register;

import org.apache.skywalking.apm.collector.core.data.CommonTable;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressTable extends CommonTable {
    public static final String TABLE = "network_address";
    public static final String COLUMN_NETWORK_ADDRESS = "network_address";
    public static final String COLUMN_SPAN_LAYER = "span_layer";
    public static final String COLUMN_SERVER_TYPE = "server_type";
    public static final String COLUMN_ADDRESS_ID = "address_id";
}
