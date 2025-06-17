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

package org.apache.skywalking.oal.rt.parser;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oal.rt.util.ClassMethodUtil;

@Getter
@Setter
public class SourceColumn {
    private String fieldName;
    private String columnName;
    private Class<?> type;
    private String typeName;
    private boolean isID;
    private int length;
    private String fieldSetter;
    private String fieldGetter;
    private final int shardingKeyIdx;
    private final boolean attribute;

    public SourceColumn(String fieldName, String columnName, Class<?> type, boolean isID, int length,
                        int shardingKeyIdx, boolean attribute) {
        this.fieldName = fieldName;
        this.columnName = columnName;
        this.type = type;
        this.typeName = type.getName();
        this.isID = isID;
        this.length = length;

        this.fieldGetter = ClassMethodUtil.toGetMethod(fieldName);
        this.fieldSetter = ClassMethodUtil.toSetMethod(fieldName);
        this.shardingKeyIdx = shardingKeyIdx;
        this.attribute = attribute;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
        this.fieldGetter = ClassMethodUtil.toGetMethod(fieldName);
        this.fieldSetter = ClassMethodUtil.toSetMethod(fieldName);
    }

    public void setTypeName(String typeName) {
        switch (typeName) {
            case "int":
                this.type = int.class;
                break;
            case "long":
                this.type = long.class;
                break;
            case "string":
            case "String":
                this.type = String.class;
                typeName = "String";
                break;
            default:
                try {
                    this.type = Class.forName(typeName);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
        }

        this.typeName = typeName;
    }

    /**
     * @return true if this column is a part of sharding key
     */
    public boolean isShardingKey() {
        return this.shardingKeyIdx > -1;
    }

    @Override
    public String toString() {
        return "SourceColumn{" + "fieldName=" + fieldName + ", columnName=" + columnName + ", type=" + type + ", isID=" + isID + ", shardingKeyIdx=" + shardingKeyIdx + ", isAttribute=" + attribute + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SourceColumn column = (SourceColumn) o;
        return isID == column.isID && Objects.equals(fieldName, column.fieldName) && Objects.equals(columnName, column.columnName) && Objects
            .equals(type, column.type) && Objects.equals(typeName, column.typeName) && Objects.equals(fieldSetter, column.fieldSetter) && Objects
            .equals(fieldGetter, column.fieldGetter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, columnName, type, typeName, isID, fieldSetter, fieldGetter);
    }
}
