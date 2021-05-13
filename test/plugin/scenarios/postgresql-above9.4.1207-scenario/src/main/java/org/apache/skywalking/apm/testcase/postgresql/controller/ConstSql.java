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

package org.apache.skywalking.apm.testcase.postgresql.controller;

public class ConstSql {
    public static final String TEST_SQL = "SELECT 1";
    public static final String CREATE_TABLE_SQL = "CREATE TABLE test_007(\n" + "id VARCHAR(1) PRIMARY KEY, \n" + "value VARCHAR(1) NOT NULL)";
    public static final String INSERT_DATA_SQL = "INSERT INTO test_007(id, value) VALUES(?,?)";
    public static final String DROP_TABLE_SQL = "DROP table test_007";
}
