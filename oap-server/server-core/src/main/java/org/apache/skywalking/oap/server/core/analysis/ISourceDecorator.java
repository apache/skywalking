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

package org.apache.skywalking.oap.server.core.analysis;

import org.apache.skywalking.oap.server.core.source.ISource;

/**
 * Source decorate extension for OAL, the implement class should be under the package `org.apache.skywalking`.
 * @param <SOURCE> The source type to be decorated, for now, only support {@link org.apache.skywalking.oap.server.core.source}
 */
public interface ISourceDecorator<SOURCE extends ISource> {
    /**
     * The scope of the source which will be decorated.
     * It's defined in the {@link org.apache.skywalking.oap.server.core.source.DefaultScopeDefine}
     */
    int getSourceScope();

    /**
     * Decorate the source, such as fill the Service attr0...attr4 through the original Service fields for store and query.
     * @param source The source instance to be decorated
     */
    void decorate(SOURCE source);
}
