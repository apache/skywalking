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

package org.apache.skywalking.oap.server.core;

/**
 * @author peng-yongsheng
 */
public class Const {
    public static final int NONE = 0;
    public static final String ID_SPLIT = "_";
    public static final String KEY_VALUE_SPLIT = ",";
    public static final String ARRAY_SPLIT = "|";
    public static final String ARRAY_PARSER_SPLIT = "\\|";
    public static final int USER_SERVICE_ID = 1;
    public static final int USER_INSTANCE_ID = 1;
    public static final int USER_ENDPOINT_ID = 1;
    public static final String NONE_ENDPOINT_NAME = "None";
    public static final String USER_CODE = "User";
    public static final String SEGMENT_SPAN_SPLIT = "S";
    public static final String UNKNOWN = "Unknown";
    public static final String EXCEPTION = "Exception";
    public static final String EMPTY_STRING = "";
    public static final int SPAN_TYPE_VIRTUAL = 9;
    public static final String DOMAIN_OPERATION_NAME = "{domain}";
}
