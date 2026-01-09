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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.skywalking.banyandb.stream.v1.BanyandbStream;

/**
 * StreamQueryResponse represents the stream query result.
 */
public class StreamQueryResponse {
    @Getter
    private final List<Element> elements;

    @Getter
    private final Trace trace;

    StreamQueryResponse(BanyandbStream.QueryResponse response) {
        final List<BanyandbStream.Element> elementsList = response.getElementsList();
        elements = new ArrayList<>(elementsList.size());
        elementsList.forEach(element -> elements.add(Element.create(element)));
        this.trace = Trace.convertFromProto(response.getTrace());
    }

    /**
     * @return size of the response set.
     */
    public int size() {
        return elements.size();
    }
}
