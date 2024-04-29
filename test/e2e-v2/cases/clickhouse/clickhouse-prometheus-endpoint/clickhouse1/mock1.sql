-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

create table default.my_table_replica
(
    id  int,
    name String,
    created_at DateTime
)
    ENGINE = ReplicatedMergeTree('/clickhouse/tables/{layer}-{shard}/my_table_replica', '{replica}')
    order by (created_at);

create table default.my_table as default.my_table_replica
    ENGINE = Distributed(cluster_1s_2r, default, my_table_replica, rand());

INSERT INTO default.my_table_replica (id, name, created_at) VALUES (1, 'third_party', '2024-02-27 16:17:34');

INSERT INTO default.my_table (id, name, created_at) VALUES (2, 'third_party2', '2024-02-28 15:44:41');
