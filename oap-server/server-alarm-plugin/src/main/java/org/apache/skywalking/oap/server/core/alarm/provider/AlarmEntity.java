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

package org.apache.skywalking.oap.server.core.alarm.provider;

import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class AlarmEntity {
    private final String scope;
    private final int scopeId;
    private final String name;
    private final String id0;
    private final String id1;
    /**
     * Layers the alarmed entity belongs to at alarm-mint time. Excluded from
     * equality / hashCode so two events on the same entity coalesce into the
     * same Window even if the metadata layer list is transient.
     */
    @EqualsAndHashCode.Exclude
    private final List<String> layers;

    public AlarmEntity(final String scope, final int scopeId, final String name,
                       final String id0, final String id1) {
        this(scope, scopeId, name, id0, id1, Collections.emptyList());
    }
}
