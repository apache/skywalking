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

package org.apache.skywalking.apm.agent.core.logging.api;

/**
 * The Log interface. It's very easy to understand, like any other log-component. Do just like log4j or log4j2 does.
 * <p>
 */
public interface ILog {
    void info(String format);

    void info(String format, Object... arguments);

    void info(Throwable t, String format, Object... arguments);

    void warn(String format, Object... arguments);

    void warn(Throwable e, String format, Object... arguments);

    void error(String format, Throwable e);

    void error(Throwable e, String format, Object... arguments);

    boolean isDebugEnable();

    boolean isInfoEnable();

    boolean isWarnEnable();

    boolean isErrorEnable();

    boolean isTraceEnabled();

    void debug(String format);

    void debug(String format, Object... arguments);

    void debug(Throwable t, String format, Object... arguments);

    void error(String format);

    void trace(String format);

    void trace(String format, Object... arguments);

    void trace(Throwable t, String format, Object... arguments);
}
