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

package org.apache.skywalking.apm.agent.core.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Some utility methods for collections. Reinvent the wheels because importing third-party libs just for some methods is
 * not worthwhile in agent side
 *
 * @since 7.0.0
 */
public final class CollectionUtil {
    public static String toString(final Map<String, String[]> map) {
        return map.entrySet()
                  .stream()
                  .map(entry -> entry.getKey() + "=" + Arrays.toString(entry.getValue()))
                  .collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("rawtypes")
    public static boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }
}
