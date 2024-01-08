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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import com.google.gson.JsonObject;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.storage.model.ElasticSearchExtension;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;

public class ColumnTypeEsMapping {

    public String transform(Class<?> type, Type genericType, int length, boolean storageOnly, final ElasticSearchExtension elasticSearchExtension) {
        if (Integer.class.equals(type) || int.class.equals(type) || Layer.class.equals(type)) {
            return "integer";
        } else if (Long.class.equals(type) || long.class.equals(type)) {
            return "long";
        } else if (Double.class.equals(type) || double.class.equals(type)) {
            return "double";
        } else if (String.class.equals(type) && storageOnly && length > 32766) {   // 32766 is the max length of a keywords field in ES
            return "text";
        } else if (String.class.equals(type) || elasticSearchExtension.isKeyword()) {
            return "keyword";
        } else if (StorageDataComplexObject.class.isAssignableFrom(type)) {
            return "text";
        } else if (byte[].class.equals(type)) {
            return "binary";
        } else if (JsonObject.class.equals(type)) {
            return "text";
        } else if (List.class.isAssignableFrom(type)) {
            final Type elementType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
            return transform((Class<?>) elementType, elementType, length, storageOnly, elasticSearchExtension);
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type.getName());
        }
    }
}
