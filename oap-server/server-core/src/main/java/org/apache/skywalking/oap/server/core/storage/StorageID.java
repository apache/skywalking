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

import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * StorageID represents an identification for the metric or the record.
 * Typically, an ID is composited by two parts
 * 1. Time bucket based on downsampling.
 * 2. The encoded entity ID, such as Service ID.
 *
 * In the SQL database and ElasticSearch, the string ID is preferred.
 * In the BanyanDB, time series and entity ID(series ID) would be treated separately.
 *
 * @since 9.4.0 StorageID replaced the `string id()` method in the StorageData. An object-oriented ID provides a more
 * friendly interface for various database implementation.
 */
@EqualsAndHashCode(of = {
    "fragments"
})
public class StorageID {
    private final List<Fragment> fragments;
    /**
     * Once the storage ID was {@link #build()} or {@link #read()},
     * this object would switch to the sealed status, no more append is allowed.
     */
    private boolean sealed = false;
    /**
     * The string ID would only be built once.
     */
    private String builtID;

    public StorageID() {
        fragments = new ArrayList<>(2);
    }

    public StorageID append(String name, String value) {
        if (StringUtil.isBlank(name)) {
            throw new IllegalArgumentException("The name of storage ID should not be null or empty.");
        }
        if (sealed) {
            throw new IllegalStateException("The storage ID is sealed. Can't append a new fragment, name=" + name);
        }
        fragments.add(new Fragment(new String[] {name}, String.class, false, value));
        return this;
    }

    public StorageID append(String name, long value) {
        if (StringUtil.isBlank(name)) {
            throw new IllegalArgumentException("The name of storage ID should not be null or empty.");
        }
        if (sealed) {
            throw new IllegalStateException("The storage ID is sealed. Can't append a new fragment, name=" + name);
        }
        fragments.add(new Fragment(new String[] {name}, Long.class, false, value));
        return this;
    }

    public StorageID append(String name, int value) {
        if (StringUtil.isBlank(name)) {
            throw new IllegalArgumentException("The name of storage ID should not be null or empty.");
        }
        if (sealed) {
            throw new IllegalStateException("The storage ID is sealed. Can't append a new fragment, name=" + name);
        }
        fragments.add(new Fragment(new String[] {name}, Integer.class, false, value));
        return this;
    }

    public StorageID appendMutant(String[] source, long value) {
        if (sealed) {
            throw new IllegalStateException("The storage ID is sealed. Can't append a new fragment, source=" + Arrays.toString(source));
        }
        fragments.add(new Fragment(source, Long.class, true, value));
        return this;
    }

    public StorageID appendMutant(final String[] source, final String value) {
        if (sealed) {
            throw new IllegalStateException("The storage ID is sealed. Can't append a new fragment, source=" + Arrays.toString(source));
        }
        fragments.add(new Fragment(source, String.class, true, value));
        return this;
    }

    /**
     * @return the string ID concatenating the values of {@link #fragments} by the underline(_).
     */
    public String build() {
        sealed = true;
        if (builtID == null) {
            builtID = Joiner.on(Const.ID_CONNECTOR).join(fragments);
        }
        return builtID;
    }

    /**
     * @return a read-only list to avoid unexpected change for metric ID.
     */
    public List<Fragment> read() {
        sealed = true;
        return Collections.unmodifiableList(fragments);
    }

    @RequiredArgsConstructor
    @Getter
    @EqualsAndHashCode(of = {
        "name",
        "value"
    }, doNotUseGetters = true)
    public static class Fragment {
        /**
         * The column name of the value, or the original column names of the mutate value.
         *
         * The names could be
         * 1. Always one column if this is not {@link #mutate} and from a certain persistent column.
         * 2. Be null if {@link #mutate} is true and no relative column, such as the original value is not in
         * the persistence.
         * 3. One or multi-values if the value is built through a symmetrical or asymmetrical encoding algorithm.
         */
        private final String[] name;
        /**
         * Represent the class type of the {@link #value}.
         */
        private final Class<?> type;
        /**
         * If true, the field was from {@link #name}, and value is changed by internal rules.
         * Such as time bucket downsampling, use a day-level time-bucket to build the ID for a minute dimension metric.
         */
        private final boolean mutate;
        private final Object value;

        public Optional<String[]> getName() {
            return Optional.ofNullable(name);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
