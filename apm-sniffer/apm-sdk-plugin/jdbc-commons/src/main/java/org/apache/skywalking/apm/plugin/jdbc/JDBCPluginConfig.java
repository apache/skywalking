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

package org.apache.skywalking.apm.plugin.jdbc;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class JDBCPluginConfig {
    public static class Plugin {
        @PluginConfig(root = JDBCPluginConfig.class)
        public static class JDBC {
            /**
             * If set to true, the parameters of the sql (typically {@link java.sql.PreparedStatement}) would be
             * collected.
             */
            public static boolean TRACE_SQL_PARAMETERS = false;
            /**
             * For the sake of performance, SkyWalking won't save the entire parameters string into the tag, but only
             * the first {@code SQL_PARAMETERS_MAX_LENGTH} characters.
             * <p>
             * Set a negative number to save the complete parameter string to the tag.
             */
            public static int SQL_PARAMETERS_MAX_LENGTH = 512;
            /**
             * For the sake of performance, SkyWalking won't save the entire sql body into the tag, but only the first
             * {@code SQL_BODY_MAX_LENGTH} characters.
             * <p>
             * Set a negative number to save the complete sql body to the tag.
             */
            public static int SQL_BODY_MAX_LENGTH = 2048;
        }
    }
}
