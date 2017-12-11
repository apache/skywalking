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


package org.apache.skywalking.apm.collector.core.util;

/**
 * @author peng-yongsheng
 */
public enum ColumnNameUtils {
    INSTANCE;

    public String rename(String columnName) {
        StringBuilder renamedColumnName = new StringBuilder();
        char[] chars = columnName.toLowerCase().toCharArray();

        boolean findUnderline = false;
        for (char character : chars) {
            if (character == '_') {
                findUnderline = true;
            } else if (findUnderline) {
                renamedColumnName.append(String.valueOf(character).toUpperCase());
                findUnderline = false;
            } else {
                renamedColumnName.append(character);
            }
        }
        return renamedColumnName.toString();
    }
}
