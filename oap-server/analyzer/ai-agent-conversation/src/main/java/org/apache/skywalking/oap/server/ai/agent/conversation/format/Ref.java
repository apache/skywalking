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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A reference into the landed records: the file by <code>seq</code>, the line by <code>row</code>, and, when the
 * node stands on one part of a record, the part by <code>block</code>.
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
public final class Ref {
    private final long seq;
    private final long row;
    @Nullable
    private final Integer block;

    @Nullable
    public static Ref of(@Nullable final JsonObject json) {
        if (json == null) {
            return null;
        }
        return new Ref(
            json.get("seq").getAsLong(),
            json.get("row").getAsLong(),
            json.has("block") && !json.get("block").isJsonNull() ? json.get("block").getAsInt() : null
        );
    }

    /**
     * @return the reference as a view map, in the order the format page lists the keys
     */
    public Map<String, Object> toMap() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("seq", seq);
        out.put("row", row);
        if (block != null) {
            out.put("block", block);
        }
        return out;
    }
}
