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

package org.apache.skywalking.oap.server.core.storage;

import java.util.HashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * StorageBuilderFactory provides the capabilities to override the default storage builders, which are implementations
 * of {@link StorageHashMapBuilder}.
 *
 * Typically, the storage needs to provide a more native format rather than {@link java.util.HashMap}.
 */
public interface StorageBuilderFactory extends Service {
    /**
     * @return the builder definition for OAL Engine.
     */
    BuilderTemplateDefinition builderTemplate();

    /**
     * Fetch the real builder by the given type of stream data and the static declared by the {@link Stream#builder()}.
     *
     * @param dataType       of the stream data.
     * @param defaultBuilder static builder.
     * @return the builder used in the runtime.
     */
    Class<? extends StorageBuilder> builderOf(Class<? extends StorageData> dataType,
                                              Class<? extends StorageBuilder> defaultBuilder);

    @Getter
    @RequiredArgsConstructor
    class BuilderTemplateDefinition {
        /**
         * The parent class of the generator builder.
         */
        private final String superClass;
        /**
         * This folder includes entity2Storage.ftl and storage2Entity.ftl to support the builder's generation.
         */
        private final String templatePath;
    }

    /**
     * The default storage builder. Use {@link StorageHashMapBuilder} to provide general suitable entity builder
     * implementation, which deliver {@link HashMap} to storage module implementation.
     */
    class Default implements StorageBuilderFactory {
        @Override
        public BuilderTemplateDefinition builderTemplate() {
            return new BuilderTemplateDefinition(
                StorageHashMapBuilder.class.getName(), "metrics-builder");
        }

        @Override
        public Class<? extends StorageBuilder> builderOf(final Class<? extends StorageData> dataType,
                                                         final Class<? extends StorageBuilder> defaultBuilder) {
            return defaultBuilder;
        }
    }
}
