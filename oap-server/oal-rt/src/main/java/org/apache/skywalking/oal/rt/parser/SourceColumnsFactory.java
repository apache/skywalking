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

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDefaultColumn;

public class SourceColumnsFactory {
    public static List<SourceColumn> getColumns(String source) {
        List<SourceColumn> sourceColumns = new ArrayList<>();

        List<ScopeDefaultColumn> columns = DefaultScopeDefine.getDefaultColumns(source);
        for (ScopeDefaultColumn defaultColumn : columns) {
            sourceColumns.add(
                new SourceColumn(defaultColumn.getFieldName(), defaultColumn.getColumnName(), defaultColumn
                    .getType(), defaultColumn.isID(), defaultColumn.getLength()));
        }
        return sourceColumns;
    }
}
