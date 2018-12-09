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

package org.apache.skywalking.oal.tool.parser;

import lombok.*;
import org.apache.skywalking.oal.tool.util.ClassMethodUtil;

@Getter(AccessLevel.PUBLIC)
@Setter(AccessLevel.PUBLIC)
public class SourceColumn {
    private String fieldName;
    private String columnName;
    private Class<?> type;
    private String typeName;
    private boolean isID;
    private String fieldSetter;
    private String fieldGetter;

    public SourceColumn(String fieldName, String columnName, Class<?> type, boolean isID) {
        this.fieldName = fieldName;
        this.columnName = columnName;
        this.type = type;
        this.typeName = type.getName();
        this.isID = isID;

        this.fieldGetter = ClassMethodUtil.toGetMethod(fieldName);
        this.fieldSetter = ClassMethodUtil.toSetMethod(fieldName);
    }

    @Override public String toString() {
        return "SourceColumn{" +
            "fieldName='" + fieldName + '\'' +
            ", columnName='" + columnName + '\'' +
            ", type=" + type +
            ", isID=" + isID +
            '}';
    }
}
