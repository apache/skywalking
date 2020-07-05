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

import org.apache.skywalking.apm.agent.core.logging.core.PatternLogResolver;

/**
 * LogManager is the {@link LogResolver} implementation manager. By using {@link LogResolver}, {@link
 * LogManager#getLogger(Class)} returns a {@link ILog} implementation. This module use this class as the main entrance,
 * and block the implementation detail about log-component. In different modules, like server or sniffer, it will use
 * different implementations.
 *
 * <p> If no {@link LogResolver} is registered, return {@link NoopLogger#INSTANCE} to avoid
 * {@link NullPointerException}. If {@link LogManager#setLogResolver(LogResolver)} is called twice, the second will
 * override the first without any warning or exception.
 *
 * <p> Created by xin on 2016/11/10.
 */
public class LogManager {
    private static LogResolver RESOLVER = new PatternLogResolver();

    public static void setLogResolver(LogResolver resolver) {
        LogManager.RESOLVER = resolver;
    }

    public static ILog getLogger(Class<?> clazz) {
        if (RESOLVER == null) {
            return NoopLogger.INSTANCE;
        }
        return LogManager.RESOLVER.getLogger(clazz);
    }

    public static ILog getLogger(String clazz) {
        if (RESOLVER == null) {
            return NoopLogger.INSTANCE;
        }
        return LogManager.RESOLVER.getLogger(clazz);
    }
}
