/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.core.module;

/**
 * @author peng-yongsheng
 */
public abstract class ModuleRegistration {

    public abstract Value buildValue();

    public static class Value {
        private final String host;
        private final int port;
        private final String contextPath;

        public Value(String host, int port, String contextPath) {
            this.host = host;
            this.port = port;
            this.contextPath = contextPath;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getHostPort() {
            return host + ":" + port;
        }

        public String getContextPath() {
            return contextPath;
        }
    }
}