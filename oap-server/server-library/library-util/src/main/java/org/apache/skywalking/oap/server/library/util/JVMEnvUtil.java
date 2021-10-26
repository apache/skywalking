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

package org.apache.skywalking.oap.server.library.util;

/**
 * JVMEnvUtil provides the metadata of current JVM.
 */
public class JVMEnvUtil {
    private static final String JVM_VERSION = System.getProperty("java.version");
    private static int JVM_MAJOR_VERSION = -1;

    public static int version() {
        if (JVM_MAJOR_VERSION < 0) {
            final String[] versionSegments = JVM_VERSION.split("\\.");
            if (versionSegments[0].equals("1")) {
                JVM_MAJOR_VERSION = Integer.parseInt(versionSegments[1]);
            } else {
                JVM_MAJOR_VERSION = Integer.parseInt(versionSegments[0]);
            }
        }
        return JVM_MAJOR_VERSION;
    }
}
