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

package org.apache.skywalking.oap.server.core.storage.annotation;

import java.lang.reflect.Field;
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.storage.define.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ColumnAnnotationRetrieval {

    private static final Logger logger = LoggerFactory.getLogger(ColumnAnnotationRetrieval.class);

    public List<ColumnDefine> retrieval(Class<Indicator> indicatorClass) {
        if (logger.isDebugEnabled()) {
            logger.debug("Retrieval column annotation from class {}", indicatorClass.getName());
        }
        List<ColumnDefine> columnDefines = new LinkedList<>();
        retrieval(indicatorClass, columnDefines);
        return columnDefines;
    }

    private void retrieval(Class clazz, List<ColumnDefine> columnDefines) {
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                columnDefines.add(new ColumnDefine(new ColumnName(column.columnName(), column.columnName()), field.getType()));
                if (logger.isDebugEnabled()) {
                    logger.debug("The field named {} with the {} type", column.columnName(), field.getType());
                }
            }
        }

        if (Objects.nonNull(clazz.getSuperclass())) {
            retrieval(clazz.getSuperclass(), columnDefines);
        }
    }
}
