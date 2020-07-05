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

package org.apache.skywalking.apm.plugin.jdbc.define;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Constants {
    public static final String CREATE_STATEMENT_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.jdbc.JDBCStatementInterceptor";

    public static final String PREPARE_STATEMENT_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.jdbc.JDBCPrepareStatementInterceptor";

    public static final String PREPARE_CALL_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.jdbc.JDBCPrepareCallInterceptor";

    public static final String SERVICE_METHOD_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.jdbc.ConnectionServiceMethodInterceptor";

    public static final String PREPARE_STATEMENT_METHOD_NAME = "prepareStatement";

    public static final String PREPARE_CALL_METHOD_NAME = "prepareCall";

    public static final String CREATE_STATEMENT_METHOD_NAME = "createStatement";

    public static final String COMMIT_METHOD_NAME = "commit";

    public static final String ROLLBACK_METHOD_NAME = "rollback";

    public static final String CLOSE_METHOD_NAME = "close";

    public static final String RELEASE_SAVE_POINT_METHOD_NAME = "releaseSavepoint";
    public static final String SQL_PARAMETER_PLACEHOLDER = "?";
    public static final Set<String> PS_SETTERS = new HashSet<String>(Arrays.asList("setArray", "setBigDecimal", "setBoolean", "setByte", "setDate", "setDouble", "setFloat", "setInt", "setLong", "setNString", "setObject", "setRowId", "setShort", "setString", "setTime", "setTimestamp", "setURL"));
    public static final Set<String> PS_IGNORABLE_SETTERS = new HashSet<String>(Arrays.asList("setAsciiStream", "setBinaryStream", "setBlob", "setBytes", "setCharacterStream", "setClob", "setNCharacterStream", "setNClob", "setRef", "setSQLXML", "setUnicodeStream"));

    public static final String PREPARED_STATEMENT_SETTER_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.JDBCPreparedStatementSetterInterceptor";
    public static final String PREPARED_STATEMENT_NULL_SETTER_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.JDBCPreparedStatementNullSetterInterceptor";
    public static final String PREPARED_STATEMENT_IGNORABLE_SETTER_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.JDBCPreparedStatementIgnorableSetterInterceptor";
}
