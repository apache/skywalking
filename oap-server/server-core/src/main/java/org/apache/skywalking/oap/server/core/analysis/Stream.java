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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.TopNStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

/**
 * Stream annotation represents a metadata definition. Include the key values of the distributed streaming calculation.
 * See {@link MetricsStreamProcessor}, {@link RecordStreamProcessor}, {@link TopNStreamProcessor} and {@link
 * NoneStreamProcessor} for more details.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Stream {
    /**
     * @return name of this stream definition.
     */
    String name();

    /**
     * @return scope id, see {@link ScopeDeclaration}
     */
    int scopeId();

    /**
     * @return the converter type between entity and storage record persistence. The converter could be override by the
     * storage implementation if necessary. Default, return {@link org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder}
     * for general suitable.
     */
    Class<? extends StorageBuilder> builder();

    /**
     * @return the stream processor type, see {@link MetricsStreamProcessor}, {@link RecordStreamProcessor},  {@link
     * TopNStreamProcessor} and {@link NoneStreamProcessor} for more details.
     */
    Class<? extends StreamProcessor> processor();
}
