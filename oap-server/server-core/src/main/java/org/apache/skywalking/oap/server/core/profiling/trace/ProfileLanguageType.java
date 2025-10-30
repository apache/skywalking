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
 */

package org.apache.skywalking.oap.server.core.profiling.trace;

/**
 * Language type for profile records. Stored as int in storage for compatibility.
 */
public enum ProfileLanguageType {
    JAVA(0),
    GO(1);

    private final int value;

    ProfileLanguageType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ProfileLanguageType fromValue(int value) {
        for (ProfileLanguageType language : values()) {
            if (language.value == value) {
                return language;
            }
        }
        return JAVA; // default to Java
    }
}


