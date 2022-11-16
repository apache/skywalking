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

package org.apache.skywalking.oap.server.core.analysis.record;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;

/**
 * LongText represents a string field, but the length is not predictable and could be longer than 1000.
 * This is a wrapper of Java String only, which provides an indicator of long text field.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class LongText implements StorageDataComplexObject<LongText> {
    private String text;

    public LongText(final String text) {
        this.text = text;
    }

    @Override
    public String toStorageData() {
        return text;
    }

    @Override
    public void toObject(final String data) {
        this.text = data;
    }

    @Override
    public void copyFrom(final LongText source) {
        this.text = source.text;
    }
}
