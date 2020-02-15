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

package org.apache.skywalking.oap.server.core.worker;

import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Worker instance register interface. Push the worker name, instance and class type having {@link Stream} annotation.
 */
public interface IWorkerInstanceSetter extends Service {
    /**
     * @param remoteReceiverWorkName worker name
     * @param instance The worker instance processes the given streamDataClass.
     * @param streamDataClass Type of metrics.
     */
    void put(String remoteReceiverWorkName, AbstractWorker instance, Class<? extends StreamData> streamDataClass);
}
