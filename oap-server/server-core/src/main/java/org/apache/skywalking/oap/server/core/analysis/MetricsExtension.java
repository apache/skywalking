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

/**
 * MetricsExtension annotation defines extension attributes of the {@link Stream} with {@link MetricsStreamProcessor}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MetricsExtension {
    /**
     * @return true if this metrics stream support down sampling.
     */
    boolean supportDownSampling();

    /**
     * @return true if this metrics data could be updated.
     */
    boolean supportUpdate();

    /**
     * @return true means the ID of this metric entity would generate timestamp related ID, such as 20170128-serviceId.
     * If as false, then, ID would be like serviceId directly. This is typically used for metadata level metric, such as
     * {@link org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic}
     */
    boolean timeRelativeID() default false;
}
