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

package org.apache.skywalking.apm.plugin.influxdb.define;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * InfluxDB plugin Constants
 *
 * @since 2020/6/6
 */
public class Constants {

  public static final String DB_TYPE = "InfluxDB";

  public static final String PING_METHOD = "ping";
  public static final String WRITE_METHOD = "write";
  public static final String QUERY_METHOD = "query";
  public static final String CREATE_DATABASE_METHOD = "createDatabase";
  public static final String DELETE_DATABASE_METHOD = "deleteDatabase";
  public static final String FLUSH_METHOD = "flush";
  public static final String CREATE_RETENTION_POLICY_METHOD = "createRetentionPolicy";
  public static final String DROP_RETENTION_POLICY_METHOD = "dropRetentionPolicy";

  public static final Set<String> MATCHER_METHOD_NAME = new HashSet<>(Arrays.asList(PING_METHOD, WRITE_METHOD, QUERY_METHOD, CREATE_DATABASE_METHOD, DELETE_DATABASE_METHOD, FLUSH_METHOD, CREATE_RETENTION_POLICY_METHOD, DROP_RETENTION_POLICY_METHOD));

}
