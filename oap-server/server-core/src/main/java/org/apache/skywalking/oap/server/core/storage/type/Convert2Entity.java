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

import java.util.function.Function;

/**
 * A function supplier to convert raw data from database to object defined in OAP
 */
public interface Convert2Entity {
    Object get(String fieldName);

    /**
     * Use the given type decoder to decode value of given field name.
     *
     * @param fieldName   to read value
     * @param typeDecoder to decode the value
     * @return decoded value
     */
    <T, R> R getWith(String fieldName, Function<T, R> typeDecoder);
}
