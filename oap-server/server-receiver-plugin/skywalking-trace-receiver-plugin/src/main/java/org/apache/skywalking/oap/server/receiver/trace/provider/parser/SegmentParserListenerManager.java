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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.SpanListenerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentParserListenerManager implements ISegmentParserListenerManager {

    private final List<SpanListenerFactory> spanListenerFactories;

    public SegmentParserListenerManager() {
        this.spanListenerFactories = new LinkedList<>();
    }

    @Override
    public void add(SpanListenerFactory spanListenerFactory) {
        spanListenerFactories.add(spanListenerFactory);
    }

    List<SpanListenerFactory> getSpanListenerFactories() {
        return spanListenerFactories;
    }
}
