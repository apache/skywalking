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

package org.apache.skywalking.library.banyandb.v1.client;

import lombok.Getter;
import org.apache.skywalking.banyandb.stream.v1.BanyandbStream;

/**
 * Element represents an entity in a Stream.
 */
@Getter
public class Element extends RowEntity {
    /**
     * identity of the element.
     * For a trace entity, it is the spanID of a Span or the segmentId of a segment in Skywalking,
     */
    protected final String id;

    public static Element create(BanyandbStream.Element element) {
        return new Element(element);
    }

    private Element(BanyandbStream.Element element) {
        super(element.getTimestamp(), element.getTagFamiliesList());
        this.id = element.getElementId();
    }
}
