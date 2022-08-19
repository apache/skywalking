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

import java.util.List;

/**
 * A function supplier to accept key-value pair, and convert to the expected database structure according to storage
 * implementation.
 *
 * @param <R> Type of database required structure.
 */
public interface Convert2Storage<R> {
    /**
     * Accept general type key/value.
     */
    void accept(String fieldName, Object fieldValue);

    /**
     * Accept String key and byte array value.
     */
    void accept(String fieldName, byte[] fieldValue);

    /**
     * Accept String key and String list value.
     */
    void accept(String fieldName, List<String> fieldValue);

    Object get(String fieldName);

    /**
     * @return the converted data
     */
    R obtain();
}