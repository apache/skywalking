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

package org.apache.skywalking.oap.log.analyzer.dsl;

import java.util.Map;
import java.util.regex.Matcher;
import lombok.Getter;
import org.apache.skywalking.apm.network.logging.v3.LogData;

/**
 * The binding bridge between OAP and the DSL, which provides some convenient methods to ease the use of the raw {@link groovy.lang.Binding#setProperty(java.lang.String, java.lang.Object)} and {@link
 * groovy.lang.Binding#getProperty(java.lang.String)}.
 */
public class Binding extends groovy.lang.Binding {
    public static final String KEY_LOG = "log";

    public static final String KEY_PARSED = "parsed";

    public static final String KEY_SAVE = "save";

    public static final String KEY_ABORT = "abort";

    public Binding() {
        setProperty(KEY_PARSED, new Parsed());
    }

    public Binding log(final LogData.Builder log) {
        setProperty(KEY_LOG, log);
        setProperty(KEY_SAVE, true);
        setProperty(KEY_ABORT, false);
        return this;
    }

    public Binding log(final LogData log) {
        return log(log.toBuilder());
    }

    public LogData.Builder log() {
        return (LogData.Builder) getProperty(KEY_LOG);
    }

    public Binding parsed(final Matcher parsed) {
        parsed().matcher = parsed;
        return this;
    }

    public Binding parsed(final Map<String, Object> parsed) {
        parsed().map = parsed;
        return this;
    }

    public Parsed parsed() {
        return (Parsed) getProperty(KEY_PARSED);
    }

    public Binding save() {
        setProperty(KEY_SAVE, true);
        return this;
    }

    public Binding drop() {
        setProperty(KEY_SAVE, false);
        return this;
    }

    public boolean shouldSave() {
        return (boolean) getProperty(KEY_SAVE);
    }

    public Binding abort() {
        setProperty(KEY_ABORT, true);
        return this;
    }

    public boolean shouldAbort() {
        return (boolean) getProperty(KEY_ABORT);
    }

    public static class Parsed {
        @Getter
        private Matcher matcher;

        @Getter
        private Map<String, Object> map;

        public Object getAt(final String key) {
            if (matcher != null) {
                return matcher.group(key);
            }
            if (map != null) {
                return map.get(key);
            }
            return null;
        }

        @SuppressWarnings("unused")
        public Object propertyMissing(final String name) {
            return getAt(name);
        }
    }
}
