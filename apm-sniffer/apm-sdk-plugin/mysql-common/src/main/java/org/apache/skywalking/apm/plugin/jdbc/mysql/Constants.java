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

package org.apache.skywalking.apm.plugin.jdbc.mysql;

import org.apache.skywalking.apm.agent.core.context.tag.StringTag;

public class Constants {
    public static final String CREATE_CALLABLE_STATEMENT_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.mysql.CreateCallableStatementInterceptor";
    public static final String CREATE_PREPARED_STATEMENT_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.mysql.CreatePreparedStatementInterceptor";
    public static final String CREATE_STATEMENT_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.mysql.CreateStatementInterceptor";
    public static final String PREPARED_STATEMENT_EXECUTE_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.mysql.PreparedStatementExecuteMethodsInterceptor";
    public static final String SET_CATALOG_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.mysql.SetCatalogInterceptor";
    public static final String STATEMENT_EXECUTE_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.mysql.StatementExecuteMethodsInterceptor";
    public static final String DRIVER_CONNECT_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.mysql.DriverConnectInterceptor";

    public static final StringTag SQL_PARAMETERS = new StringTag("db.sql.parameters");
}
