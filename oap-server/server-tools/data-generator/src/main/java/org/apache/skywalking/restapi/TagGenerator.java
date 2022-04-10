/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.restapi;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.skywalking.generator.Generator;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public final class TagGenerator implements Generator<Tag> {
    private Generator<String> key;
    private Generator<String> value;

    @Override
    public Tag next() {
        return new Tag(key.next(), value.next());
    }

    @Override
    public void reset() {
        key.reset();
        value.reset();
    }
}
