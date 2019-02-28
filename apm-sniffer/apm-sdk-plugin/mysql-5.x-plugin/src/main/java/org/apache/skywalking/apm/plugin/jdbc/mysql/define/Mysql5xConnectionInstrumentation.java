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


package org.apache.skywalking.apm.plugin.jdbc.mysql.define;

import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch.byMultiClassMatch;

/**
 * {@link Mysql5xConnectionInstrumentation } interceptor {@link com.mysql.cj.jdbc.ConnectionImpl} and
 * com.mysql.jdbc.ConnectionImpl in mysql jdbc driver 5.1 and 5.1+
 *
 * @author zhangxin
 */
public class Mysql5xConnectionInstrumentation extends ConnectionInstrumentation {
    public static final String ENHANCE_CLASS = "com.mysql.jdbc.ConnectionImpl";

    public static final String CJ_JDBC_ENHANCE_CLASS = "com.mysql.cj.jdbc.ConnectionImpl";

    @Override protected ClassMatch enhanceClass() {
        return byMultiClassMatch(ENHANCE_CLASS, CJ_JDBC_ENHANCE_CLASS);
    }
}
