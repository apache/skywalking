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
 * No operation logger implementation. Just implement {@link ILog} interface, but do nothing.
 * <p>
 */
public enum NoopLogger implements ILog {
    INSTANCE;

    @Override
    public void info(String message) {

    }

    @Override
    public void info(String format, Object... arguments) {

    }

    @Override
    public void info(final Throwable t, final String format, final Object... arguments) {

    }

    @Override
    public void warn(String format, Object... arguments) {

    }

    @Override
    public void error(String format, Throwable e) {

    }

    @Override
    public boolean isDebugEnable() {
        return false;
    }

    @Override
    public boolean isInfoEnable() {
        return false;
    }

    @Override
    public boolean isWarnEnable() {
        return false;
    }

    @Override
    public boolean isErrorEnable() {
        return false;
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public void debug(String format) {

    }

    @Override
    public void debug(String format, Object... arguments) {

    }

    @Override
    public void debug(final Throwable t, final String format, final Object... arguments) {

    }

    @Override
    public void error(String format) {

    }

    @Override
    public void trace(final String format) {

    }

    @Override
    public void trace(final String format, final Object... arguments) {

    }

    @Override
    public void trace(final Throwable t, final String format, final Object... arguments) {

    }

    @Override
    public void error(Throwable e, String format, Object... arguments) {

    }

    @Override
    public void warn(Throwable e, String format, Object... arguments) {

    }
}
