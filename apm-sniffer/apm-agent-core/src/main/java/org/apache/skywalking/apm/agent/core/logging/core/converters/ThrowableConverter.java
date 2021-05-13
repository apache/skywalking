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

package org.apache.skywalking.apm.agent.core.logging.core.converters;

import org.apache.skywalking.apm.agent.core.conf.Constants;
import org.apache.skywalking.apm.agent.core.logging.core.Converter;
import org.apache.skywalking.apm.agent.core.logging.core.LogEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Return the StackTrace of String with logEvent.getThrowable()
 */
public class ThrowableConverter implements Converter {
    @Override
    public String convert(LogEvent logEvent) {
        Throwable t = logEvent.getThrowable();
        return t == null ? "" : format(t);
    }

    public static String format(Throwable t) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        t.printStackTrace(new java.io.PrintWriter(buf, true));
        String expMessage = buf.toString();
        try {
            buf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Constants.LINE_SEPARATOR + expMessage;
    }

    @Override
    public String getKey() {
        return "throwable";
    }
}
