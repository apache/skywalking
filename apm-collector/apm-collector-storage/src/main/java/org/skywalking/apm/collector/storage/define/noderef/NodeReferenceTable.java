/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.define.noderef;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author peng-yongsheng
 */
public class NodeReferenceTable extends CommonTable {
    public static final String TABLE = "node_reference";
    public static final String COLUMN_FRONT_APPLICATION_ID = "front_application_id";
    public static final String COLUMN_BEHIND_APPLICATION_ID = "behind_application_id";
    public static final String COLUMN_BEHIND_PEER = "behind_peer";
    public static final String COLUMN_S1_LTE = "s1_lte";
    public static final String COLUMN_S3_LTE = "s3_lte";
    public static final String COLUMN_S5_LTE = "s5_lte";
    public static final String COLUMN_S5_GT = "s5_gt";
    public static final String COLUMN_SUMMARY = "summary";
    public static final String COLUMN_ERROR = "error";
}
