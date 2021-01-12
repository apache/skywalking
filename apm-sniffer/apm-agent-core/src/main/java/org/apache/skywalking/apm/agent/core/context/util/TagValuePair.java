/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.agent.core.context.util;

import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;

public class TagValuePair {
    private AbstractTag key;
    private String value;

    public TagValuePair(AbstractTag tag, String value) {
        this.key = tag;
        this.value = value;
    }

    public AbstractTag getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public KeyStringValuePair transform() {
        KeyStringValuePair.Builder keyValueBuilder = KeyStringValuePair.newBuilder();
        keyValueBuilder.setKey(key.key());
        if (value != null) {
            keyValueBuilder.setValue(value);
        }
        return keyValueBuilder.build();
    }

    public boolean sameWith(AbstractTag tag) {
        return key.isCanOverwrite() && key.getId() == tag.getId();
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TagValuePair))
            return false;
        final TagValuePair that = (TagValuePair) o;
        return Objects.equals(getKey(), that.getKey()) &&
            Objects.equals(getValue(), that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey(), getValue());
    }
}