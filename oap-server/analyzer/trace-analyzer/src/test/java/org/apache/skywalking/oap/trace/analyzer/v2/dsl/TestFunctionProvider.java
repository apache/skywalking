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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.TALFunctionContext;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;

/**
 * Functions a test module contributes, one of them reading the run's input through its context.
 */
public class TestFunctionProvider implements TALFunctionProvider {
    @Override
    public String module() {
        return "test-module";
    }

    @Override
    public boolean supportsInputType(final String inputType) {
        return true;
    }

    public String greet(final TALFunctionContext context, final String who) {
        return "hi " + who + " from " + ((TestNestedSpanInput) context.input()).getName();
    }

    public long twice(final long value) {
        return value * 2;
    }

    public List<String> pair(final String first, final String second) {
        return List.of(first, second);
    }

    public String layerName(final Layer layer) {
        return layer.name();
    }

    public String pointName(final DetectPoint point) {
        return point.name();
    }

    public String mixed() {
        return "m";
    }

    public long mixed(final long value) {
        return value + 1;
    }

    public String pick(final String value) {
        return "s:" + value;
    }

    public String pick(final long value) {
        return "l:" + value;
    }

    public double amount() {
        return 2.6;
    }

    public List<byte[]> chunks() {
        return List.of(new byte[] {66, 67});
    }

    public Object thing() {
        return "abc";
    }

    public Short boxedSmall() {
        return 7;
    }

    public String readByte(final byte value) {
        return "b" + value;
    }

    public String readShort(final short value) {
        return "s" + value;
    }

    public String takeByte(final Byte value) {
        return "byte:" + value;
    }

    public short small() {
        return (short) 7;
    }

    public long readObject(final Object value) {
        return value == null ? 0L : 1L;
    }

    public String any(final Object value) {
        return value.getClass().getSimpleName() + ":" + value;
    }

    public String choose(final String value) {
        return "s";
    }

    public String choose(final Object value) {
        return "o";
    }
}
