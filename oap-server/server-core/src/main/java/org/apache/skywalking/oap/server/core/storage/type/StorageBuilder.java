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

package org.apache.skywalking.oap.server.core.storage.type;

import org.apache.skywalking.oap.server.core.storage.StorageData;

/**
 * Converter between the give T and K.
 *
 * @param <T> A storage entity implementation.
 */
public interface StorageBuilder<T extends StorageData> {
    /**
     * Use the given converter to build an OAP entity object.
     *
     * @param converter to transfer data format
     * @return an OAP entity object
     */
    T storage2Entity(Convert2Entity converter);

    /**
     * Use the given converter to build a database preferred structure.
     *
     * @param entity    to be used
     * @param converter provides the converting logic and hosts the converted value. Use {@link
     *                  Convert2Storage#obtain()} to read the converted data.
     */
    void entity2Storage(T entity, Convert2Storage converter);
}
