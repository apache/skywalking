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

import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;

/**
 * @author peng-yongsheng
 */
public class StorageEntityAnnotationUtils {

    public static String getModelName(Class aClass) {
        if (aClass.isAnnotationPresent(StorageEntity.class)) {
            StorageEntity annotation = (StorageEntity)aClass.getAnnotation(StorageEntity.class);
            return annotation.name();
        } else {
            throw new UnexpectedException("Fail to get model name from class " + aClass.getSimpleName());
        }
    }

    public static boolean getDeleteHistory(Class aClass) {
        if (aClass.isAnnotationPresent(StorageEntity.class)) {
            StorageEntity annotation = (StorageEntity)aClass.getAnnotation(StorageEntity.class);
            return annotation.deleteHistory();
        } else {
            throw new UnexpectedException("Fail to get delete history tag from class " + aClass.getSimpleName());
        }
    }

    public static Class<? extends StorageBuilder> getBuilder(Class aClass) {
        if (aClass.isAnnotationPresent(StorageEntity.class)) {
            StorageEntity annotation = (StorageEntity)aClass.getAnnotation(StorageEntity.class);
            return annotation.builder();
        } else {
            throw new UnexpectedException("Fail to get entity builder from class " + aClass.getSimpleName());
        }
    }

    public static int getSourceScope(Class aClass) {
        if (aClass.isAnnotationPresent(StorageEntity.class)) {
            StorageEntity annotation = (StorageEntity)aClass.getAnnotation(StorageEntity.class);
            return annotation.sourceScopeId();
        } else {
            throw new UnexpectedException("Fail to get source scope from class " + aClass.getSimpleName());
        }
    }
}
