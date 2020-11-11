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

package org.apache.skywalking.oap.server.library.util.prometheus.parser.sample;

import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class TextSample {

    private final String name;
    private final Map<String, String> labels;
    private final String value;
    private final String line;

    public static TextSample parse(String line) {
        Context ctx = new Context();
        State state = State.NAME;
        for (int c = 0; c < line.length(); c++) {
            char charAt = line.charAt(c);
            state = state.nextState(charAt, ctx);
            if (state == State.INVALID) {
                throw new IllegalStateException(String.format("At offset %d, character is %c", c, charAt));
            } else if (state == State.END) {
                break;
            }
        }
        return new TextSample(ctx.name.toString(), ctx.labels, ctx.value.toString(), line);
    }
}