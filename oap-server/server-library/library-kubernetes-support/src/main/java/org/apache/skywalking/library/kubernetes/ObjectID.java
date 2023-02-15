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

 package org.apache.skywalking.library.kubernetes;

import org.apache.logging.log4j.util.Strings;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@RequiredArgsConstructor
@Accessors(fluent = true)
public class ObjectID {
    public static final ObjectID EMPTY = ObjectID.builder().build();

    private final String name;
    private final String namespace;

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "";
        }
        if (Strings.isBlank(namespace)) {
            return name;
        }
        return name + "." + namespace;
    }
}
