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

package org.apache.skywalking.apm.collector.cache.guava;


import com.google.common.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * @author nikitap492
 */
public class CacheUtils {
    private static final Logger logger = LoggerFactory.getLogger(CacheUtils.class);

    public static <K, V> V retrieve(Cache<K, V> cache, K key, Supplier<V> supplier) {
        V value = null;
        try {
            value = cache.get(key, supplier::get);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (isNull(value)) {
            value = supplier.get();
            if (nonNull(value)) {
                cache.put(key, value);
            }
        }

        return value;
    }

    public static <K, V> V retrieveOrElse(Cache<K, V> cache, K key, Supplier<V> supplier, V defaultValue) {
        return Optional.ofNullable(retrieve(cache, key, supplier)).orElse(defaultValue);
    }
}
