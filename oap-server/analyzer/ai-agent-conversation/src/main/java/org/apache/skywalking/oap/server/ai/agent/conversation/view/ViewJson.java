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

package org.apache.skywalking.oap.server.ai.agent.conversation.view;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Writer;
import java.util.Map;

/**
 * Writes an <code>asz.view</code> document as JSON, keys in insertion order and absent values as null, the way
 * <code>asz conversation -json</code> and the Sessionizer's viewer print it.
 */
public final class ViewJson {
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private ViewJson() {
    }

    /**
     * Streams the document into the writer as it is walked; nothing is buffered whole.
     *
     * @param document the document, as ordered maps, lists, strings, numbers and booleans
     * @param out      where the JSON goes
     */
    public static void write(final Map<String, Object> document, final Writer out) {
        GSON.toJson(document, out);
    }
}
