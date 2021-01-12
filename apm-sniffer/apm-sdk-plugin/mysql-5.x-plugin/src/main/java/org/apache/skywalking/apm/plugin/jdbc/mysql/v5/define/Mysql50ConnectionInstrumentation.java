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

package org.apache.skywalking.apm.plugin.jdbc.mysql.v5.define;

import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link Mysql50ConnectionInstrumentation} interceptor the com.mysql.jdbc.Connection class in the 5.0.x version of
 * mysql driver jar.
 */
public class Mysql50ConnectionInstrumentation extends AbstractConnectionInstrumentation {
    @Override
    protected ClassMatch enhanceClass() {
        return byName("com.mysql.jdbc.Connection");
    }

    @Override
    protected String[] witnessClasses() {
        return new String[] {"com.mysql.jdbc.CursorRowProvider"};
    }
}
