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

package org.apache.skywalking.oal.v2.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Represents a location in the OAL source file.
 * Used for error reporting and debugging.
 */
@Getter
@EqualsAndHashCode
public final class SourceLocation {
    public static final SourceLocation UNKNOWN = new SourceLocation("unknown", 0, 0);

    private final String fileName;
    private final int line;
    private final int column;

    public SourceLocation(String fileName, int line, int column) {
        this.fileName = fileName;
        this.line = line;
        this.column = column;
    }

    public static SourceLocation of(String fileName, int line, int column) {
        return new SourceLocation(fileName, line, column);
    }

    @Override
    public String toString() {
        if (this == UNKNOWN) {
            return "unknown location";
        }
        return fileName + ":" + line + ":" + column;
    }
}
