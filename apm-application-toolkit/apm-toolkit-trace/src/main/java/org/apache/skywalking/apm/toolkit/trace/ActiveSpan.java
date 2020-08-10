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

package org.apache.skywalking.apm.toolkit.trace;

/**
 * provide custom api that set tag for current active span.
 */
public class ActiveSpan {
    /**
     * @param key   tag key
     * @param value tag value
     */
    public static void tag(String key, String value) {
    }

    public static void error() {
    }

    public static void error(String errorMsg) {
    }

    public static void error(Throwable throwable) {
    }

    public static void debug(String debugMsg) {
    }

    public static void info(String infoMsg) {
    }

    public static void setOperationName(String operationName) {
    }
}
