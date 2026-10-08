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

package org.apache.skywalking.oap.server.ai.agent.conversation.withhold;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * The names a reader may withhold, as the Session Data page lists them: <code>system_prompt</code>, the system
 * prompt the runtime sent its model, and <code>tool_schemas</code>, the schemas of the tools it offered. An
 * adapter sets each as a flag on the record that carries it, and the OAP withholds by those flags, never by the
 * text or the size of a part. The names are the OAP's <code>hide</code> setting, and apply to every reader: the OAP
 * knows nothing about who is reading.
 *
 * <p>A set of names is kept sorted and without repeats, so the same set keys the same answer wherever it goes.
 */
public final class Hide {
    public static final String SYSTEM_PROMPT = "system_prompt";
    public static final String TOOL_SCHEMAS = "tool_schemas";
    /** Every name a reader may withhold, in the order the page lists them. */
    public static final List<String> NAMES = Collections.unmodifiableList(Arrays.asList(SYSTEM_PROMPT, TOOL_SCHEMAS));
    public static final List<String> NONE = Collections.emptyList();

    private Hide() {
    }

    /**
     * @param text names separated by commas, as the configuration writes them;
     *             null or blank for none
     * @return the names, sorted and without repeats
     * @throws IllegalArgumentException when a name is not one a reader may withhold
     */
    public static List<String> parse(@Nullable final String text) {
        final List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (final String name : text.split(",", -1)) {
            final String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return names(out);
    }

    /**
     * @param names names, in any order, repeats included
     * @return the names, sorted and without repeats
     * @throws IllegalArgumentException when a name is not one a reader may withhold
     */
    public static List<String> names(final Collection<String> names) {
        final TreeSet<String> set = new TreeSet<>();
        for (final String name : names) {
            if (!NAMES.contains(name)) {
                throw new IllegalArgumentException(
                    "\"" + name + "\" is not a flag a reader may withhold. Those are " + String.join(" and ", NAMES));
            }
            set.add(name);
        }
        return new ArrayList<>(set);
    }

    /**
     * @return whether the flag is one a reader may withhold
     */
    public static boolean isHideable(final String flag) {
        return NAMES.contains(flag);
    }

    /**
     * @return whether any of the flags is one a reader may withhold
     */
    public static boolean carriesHideable(final Collection<String> flags) {
        for (final String flag : flags) {
            if (isHideable(flag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether any of the flags is one of the names withheld
     */
    public static boolean carriesAny(final Collection<String> flags, final Collection<String> hidden) {
        if (hidden.isEmpty()) {
            return false;
        }
        for (final String flag : flags) {
            if (hidden.contains(flag)) {
                return true;
            }
        }
        return false;
    }
}
