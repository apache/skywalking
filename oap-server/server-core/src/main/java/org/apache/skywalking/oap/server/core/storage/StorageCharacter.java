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

package org.apache.skywalking.oap.server.core.storage;

import org.apache.skywalking.oap.server.core.source.ScopeDefaultColumn;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * StorageCharacter provides core aware characters which make the core could run optimized codes accordingly.
 *
 * @since 9.5.0
 */
public interface StorageCharacter extends Service {
    /**
     * See {@link ScopeDefaultColumn.DefinedByField#idxOfCompositeID()}
     *
     * @return true if ID is declared through existing column, but not a virtual column. Typically, there was an entity_id
     * column to represent a subject(service, endpoint, et.c)
     */
    boolean supportCompositeID();

    class Default implements StorageCharacter {

        @Override
        public boolean supportCompositeID() {
            return false;
        }
    }
}
