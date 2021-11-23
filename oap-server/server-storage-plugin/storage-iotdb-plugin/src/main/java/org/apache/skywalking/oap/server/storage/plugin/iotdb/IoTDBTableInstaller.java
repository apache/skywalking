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

package org.apache.skywalking.oap.server.storage.plugin.iotdb;

import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * The Design of Apache IoTDB Storage Option
 *
 * 1. The Data Model of IoTDB
 * We can use the tree structure to understand the data model of iotdb.
 * If divided according to layers, from high to low is: `Storage Group` -> (`LayerName`) -> `Device` -> `Measurement`.
 * From the top layer to a certain layer below it is called a Path.
 * The top layer is `Storage Group` (must start with `root.`), the penultimate layer is `Device`,
 * and the bottom layer is `Measurement`. There can be many layers in the middle,
 * and each layer is marked as `LayerName`. For more information,
 * please refer to the [Data Model and Terminology](https://iotdb.apache.org/UserGuide/V0.12.x/Data-Concept/Data-Model-and-Terminology.html)
 * in the official document of the version 0.12.x.
 *
 * 2. The Storage Schema of IoTDB Storage Plugin
 * Since each `LayerName` of IoTDB is stored in memory, it can be considered as an index,
 * and this feature can be fully utilized to improve IoTDB query performance.
 * The default storage group is `root.skywalking`, it will occupy the first and the second layer of the path.
 * The model name is stored at the next layer of the storage group (the third layer of the path),
 * such as `root.skywalking.model_name`.
 *
 * SkyWalking has its own index requirement, but it isn't applicable to IoTDB.
 * Considering query frequency and referring to the implementation of the other storage options,
 * we choose `id`, `entity_id`, `node_type`, `service_id`, `service_group`, `trace_id` as indexes
 * and fix their order in the path. If we don't fix their order, we cannot map their value to column,
 * since we only store their value in the path but don't store their column name.
 * The other columns are treated as Measurements.
 *
 * The mapping from SkyWalking data model to IoTDB data model is below.
 *
 * | SkyWalking           | IoTDB                                        |
 * | -------------------- | -------------------------------------------- |
 * | Database             | Storage Group(1st and 2nd layer of the path) |
 * | Model                | LayerName(3rd layer of the path)             |
 * | Indexed Column       | stored in memory through hard-code           |
 * | Indexed Column Value | LayerName(after 3rd layer of the path)       |
 * | Non-indexed Column   | Measurement                                  |
 * | Non-indexed Value    | the value of Measurement                     |
 *
 * #### For general example
 * There are model1('column11', column12), model2('column21', 'column22', column23), model3(column31).
 * Single quotation mark indicates that the column requires to be indexed.
 * In this example, `modelx_name` refers to the name of modelx,
 * `columnx_name` refers to the name of columnx and `columnx_value` refers to the value of columnx.
 *
 * Before these 3 model storage schema, here are some points we need to know.
 * - In order to avoid the value of indexed column contains dot(`.`),
 * all of them should be wrapped in double quotation mark since IoTDB use dot(`.`) as the separator in the path.
 * - We use `align by device` in query SQL to get a more friendly result. For more information about `align by device`,
 * please see [DML (Data Manipulation Language)](https://iotdb.apache.org/UserGuide/V0.12.x/IoTDB-SQL-Language/DML-Data-Manipulation-Language.html)
 * and [Query by device alignment](https://iotdb.apache.org/SystemDesign/DataQuery/AlignByDeviceQuery.html).
 *
 * The path of them is following:
 * - The Model with index:
 *   - `root.skywalking.model1_name.column11_value.column12_name`
 *   - `root.skywalking.model2_name.column21_value.column22_value.column23_name`
 * - The Model without index:
 *   - `root.skywalking.model3_name.column31_Name`
 *
 * Use `select * from root.skywalking.modelx_name align by device` respectively to get their schema and data.
 * The SQL result is following:
 *
 * | Time          | Device                                       | column12_name  |
 * | ------------- | -------------------------------------------- | -------------- |
 * | 1637494020000 | root.skywalking.model1_name."column11_value" | column12_value |
 *
 * | Time          | Device                                                        | column23_name  |
 * | ------------- | ------------------------------------------------------------- | -------------- |
 * | 1637494020000 | root.skywalking.model2_name."column21_value"."column22_value" | column23_value |
 *
 * | Time          | Device                      | column31_name  |
 * | ------------- | --------------------------- | -------------- |
 * | 1637494020000 | root.skywalking.model3_name | column31_value |
 *
 * #### For specific example
 * Before 5 typical examples, here are some points we need to know.
 * - The indexed columns and their order: `id`, `entity_id`, `node_type`, `service_id`, `service_group`, `trace_id`.
 * Other columns are treated as non indexed and stored as Measurement.
 * - The storage entity extends Metrics or Record contains a column `time_bucket`.
 * The `time_bucket` column in SkyWalking Model can be converted to the `timestamp` of IoTDB when inserting data.
 * We don't need to store `time_bucket` separately. In the next examples, we won't list `time_bucket` anymore.
 * - The `Time` in query result corresponds to the `timestamp` in insert SQL and API.
 *
 * 1) Metadata: service_traffic
 * service_traffic entity has 4 columns: 'id', name, 'node_type', 'service_group'.
 * When service_traffic entity includes a row with timestamp 1637494020000, the row should be as following:
 * (**Notice**: the value of service_group is null.)
 *
 * | id                             | name                 | node_type | service_group |
 * | ------------------------------ | -------------------- | --------- | ------------- |
 * | ZTJlLXNlcnZpY2UtcHJvdmlkZXI=.1 | e2e-service-provider | 0         |               |
 *
 * And the row stored in IoTDB should be as following:
 * (Query SQL: `select  from root.skywalking.service_traffic align by device`)
 *
 * | Time          | Device                                                                      | name                 |
 * | ------------- | --------------------------------------------------------------------------- | -------------------- |
 * | 1637494020000 | root.skywalking.service_traffic."ZTJlLXNlcnZpY2UtcHJvdmlkZXI=.1"."0"."null" | e2e-service-provider |
 *
 * The value of id, node_type and service_group are stored in the path in the specified order.
 * **Notice**: If those index value is null, it will be transformed to a string "null".
 *
 * 2) Metrics: service_cpm
 * service_cpm entity has 4 columns: 'id', 'service_id', total, value.
 * When service_cpm entity includes a row with timestamp 1637494020000, the row should be as following:
 *
 * | id                                          | service_id                     | total | value |
 * | ------------------------------------------- | ------------------------------ | ----- | ----- |
 * | 202111211127_ZTJlLXNlcnZpY2UtY29uc3VtZXI=.1 | ZTJlLXNlcnZpY2UtY29uc3VtZXI=.1 | 4     | 4     |
 *
 * And the row stored in IoTDB should be as following:
 * (Query SQL: `select from root.skywalking.service_cpm align by device`)
 *
 * | Time          | Device                                                                                                     | total | value |
 * | ------------- | ---------------------------------------------------------------------------------------------------------- | ----- | ----- |
 * | 1637494020000 | root.skywalking.service_cpm."202111211127_ZTJlLXNlcnZpY2UtY29uc3VtZXI=.1"."ZTJlLXNlcnZpY2UtY29uc3VtZXI=.1" | 4     | 4     |
 *
 * The value of id and service_id are stored in the path in the specified order.
 *
 * 3) Trace segment: segment
 * segment entity has 10 columns at least: 'id', segment_id, 'trace_id', 'service_id', service_instance_id,
 * endpoint_id, start_time, latency, is_error, data_binary. In addition, it could have variable number of tags.
 * When segment entity includes 2 rows with timestamp 1637494106000 and 1637494134000,
 * these rows should be as following. The `db.type` and `db.instance` are two tags.
 * The first data has two tags, and the second data doesn't have tag.
 *
 * | id   | segment_id   | trace_id   | service_id   | service_instance_id   | endpoint_id   | start_time    | latency | is_error | data_binary   | db.type | db.instance |
 * | ---- | ------------ | ---------- | ------------ | --------------------- | ------------- | ------------- | ------- | -------- | ------------- | ------- | ----------- |
 * | id_1 | segment_id_1 | trace_id_1 | service_id_1 | service_instance_id_1 | endpoint_id_1 | 1637494106515 | 1425    | 0        | data_binary_1 | sql     | testdb      |
 * | id_2 | segment_id_2 | trace_id_2 | service_id_2 | service_instance_id_2 | endpoint_id_2 | 2637494106765 | 1254    | 0        | data_binary_2 |         |             |
 *
 * And these row stored in IoTDB should be as following:
 * (Query SQL: `select from root.skywalking.segment align by device`)
 *
 * | Time          | Device                                                     | start_time    | data_binary   | latency | endpoint_id   | is_error | service_instance_id   | segment_id   | "db.type" | "db.instance" |
 * | ------------- | ---------------------------------------------------------- | ------------- | ------------- | ------- | ------------- | -------- | --------------------- | ------------ | --------- | ------------- |
 * | 1637494106000 | root.skywalking.segment."id_1"."service_id_1"."trace_id_1" | 1637494106515 | data_binary_1 | 1425    | endpoint_id_1 | 0        | service_instance_id_1 | segment_id_1 | sql       | testdb        |
 * | 1637494106000 | root.skywalking.segment."id_2"."service_id_2"."trace_id_2" | 1637494106765 | data_binary_2 | 1254    | endpoint_id_2 | 0        | service_instance_id_2 | segment_id_2 | null      | null          |
 *
 * The value of id, service_id and trace_id are stored in the path in the specified order.
 * **Notice**: If the measurement contains dot(`.`), it will be wrapped in double quotation mark since IoTDB doesn't allow it.
 * In order to align, IoTDB will append null value for those data without tag in some models.
 *
 * 4) Log
 * log entity has 12 columns at least: 'id', unique_id, 'service_id', service_instance_id, endpoint_id,
 * 'trace_id', trace_segment_id, span_id, content_type, content, tags_raw_data, timestamp.
 * In addition, it could have variable number of tags.
 * When log entity includes a row with timestamp 1637494052000, the row should be as following and the level is a tag.
 *
 * | id   | unique_id   | service_id   | service_instance_id   | endpoint_id   | trace_id   | trace_segment_id   | span_id | content_type | content   | tags_raw_data   | timestamp     | level |
 * | ---- | ----------- | ------------ | --------------------- | ------------- | ---------- | ------------------ | ------- | ------------ | --------- | --------------- | ------------- | ----- |
 * | id_1 | unique_id_1 | service_id_1 | service_instance_id_1 | endpoint_id_1 | trace_id_1 | trace_segment_id_1 | 0       | 1            | content_1 | tags_raw_data_1 | 1637494052118 | INFO  |
 *
 * And the row stored in IoTDB should be as following:
 * (Query SQL: `select from root.skywalking.log align by device`)
 *
 * | Time          | Device                                             | unique_id   | content_type | span_id | tags_raw_data   | "timestamp"   | level | service_instance_id   | content   | trace_segment_id   |
 * | ------------- | -------------------------------------------------- | ----------- | ------------ | ------- | --------------- | ------------- | ----- | --------------------- | --------- | ------------------ |
 * | 1637494052000 | root.skywalking."id_1"."service_id_1"."trace_id_1" | unique_id_1 | 1            | 0       | tags_raw_data_1 | 1637494052118 | INFO  | service_instance_id_1 | content_1 | trace_segment_id_1 |
 *
 * The value of id, service_id and trace_id are stored in the path in the specified order.
 * **Notice**: If the measurement named timestamp, it will be wrapped in double quotation mark since IoTDB doesn't allow it.
 *
 * 5) Profiling snapshots: profile_task_segment_snapshot
 * profile_task_segment_snapshot has 6 columns: 'id', task_id, segment_id, dump_time, sequence, stack_binary.
 * When profile_task_segment_snapshot includes a row with timestamp 1637494131000, the row should be as following.
 *
 * | id   | task_id   | segment_id   | dump_time     | sequence | stack_binary   |
 * | ---- | --------- | ------------ | ------------- | -------- | -------------- |
 * | id_1 | task_id_1 | segment_id_1 | 1637494131153 | 0        | stack_binary_1 |
 *
 * And the row stored in IoTDB should be as following:
 * (Query SQL: `select from root.skywalking.profile_task_segment_snapshot align by device`)
 *
 * | Time          | Device                                               | sequence | dump_time     | stack_binary   | task_id   | segment_id   |
 * | ------------- | ---------------------------------------------------- | -------- | ------------- | -------------- | --------- | ------------ |
 * | 1637494131000 | root.skywalking.profile_task_segment_snapshot."id_1" | 0        | 1637494131153 | stack_binary_1 | task_id_1 | segment_id_1 |
 *
 * The value of id is stored in the path in the specified order.
 */
public class IoTDBTableInstaller extends ModelInstaller {
    public IoTDBTableInstaller(Client client, ModuleManager moduleManager) {
        super(client, moduleManager);
    }

    @Override
    protected boolean isExists(Model model) {
        IoTDBTableMetaInfo.addModel(model);
        return true;
    }

    @Override
    protected void createTable(Model model) {
        // Automatically create table when insert data
    }
}
