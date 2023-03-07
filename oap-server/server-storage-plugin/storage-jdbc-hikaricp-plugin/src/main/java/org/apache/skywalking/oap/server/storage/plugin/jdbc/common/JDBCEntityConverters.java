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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common;

import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;

import java.sql.ResultSet;

public class JDBCEntityConverters {
    public static Convert2Entity toEntity(ResultSet resultSet) {
        return new Convert2Entity() {
            @Override
            @SneakyThrows
            public Object get(String fieldName) {
                return resultSet.getObject(fieldName);
            }

            @Override
            @SneakyThrows
            public byte[] getBytes(String fieldName) {
                return resultSet.getBytes(fieldName);
            }
        };
    }
}
