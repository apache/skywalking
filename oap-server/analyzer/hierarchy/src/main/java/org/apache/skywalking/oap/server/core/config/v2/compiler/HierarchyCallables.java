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
 */

package org.apache.skywalking.oap.server.core.config.v2.compiler;

import org.apache.skywalking.oap.server.core.dsl.DslCallables;
import org.apache.skywalking.oap.server.core.query.type.Service;

/**
 * What a hierarchy matching rule may call, built the first time a rule compiles. To support another method, add it to
 * its class here and to the guide the list names.
 */
final class HierarchyCallables {

    private HierarchyCallables() {
    }

    static DslCallables get() {
        return Holder.LIST;
    }

    private static final class Holder {
        static final DslCallables LIST = DslCallables.builder("a hierarchy rule", "the service hierarchy documentation")
            .methods(Object.class, "toString", "equals")
            .methods(String.class, DslCallables.TEXT_METHODS)
            .getters(Service.class)
            .statics(Integer.class, "parseInt", "valueOf")
            .statics(Long.class, "parseLong", "valueOf")
            .build();
    }
}
