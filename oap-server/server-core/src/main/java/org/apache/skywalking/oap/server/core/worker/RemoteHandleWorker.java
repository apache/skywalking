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

import lombok.Getter;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricStreamKind;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;

@Getter
public class RemoteHandleWorker {
    private final AbstractWorker worker;
    private final MetricStreamKind kind;
    private final Class<? extends StreamData> streamDataClass;

    private AcceptableValue<?> meterClassPrototype;

    public RemoteHandleWorker(AbstractWorker worker, MetricStreamKind kind,
                              Class<? extends StreamData> streamDataClass) {
        this.worker = worker;
        this.kind = kind;
        this.streamDataClass = streamDataClass;

        if (MetricStreamKind.MAL == kind) {
            try {
                meterClassPrototype = (AcceptableValue<?>) streamDataClass.newInstance();
            } catch (Exception e) {
                throw new UnexpectedException("Can't create mal meter prototype with stream class" + streamDataClass);
            }
        }
    }

    /**
     * Create a new StreamData instance with metadata {@link WithMetadata} for RemoteServiceHandler to deserialize the RemoteMessage.
     * OAL metrics can initialize metadata through the constructor,
     * while MAL metrics need to initialize metadata through the {@link AcceptableValue} createNew method.
     */
    public StreamData newStreamDataInstance() throws InstantiationException, IllegalAccessException {
        switch (kind) {
            case OAL:
                return streamDataClass.newInstance();
            case MAL:
                return (StreamData) meterClassPrototype.createNew();
        }
        throw new UnexpectedException("Unsupported metrics stream kind" + kind);
    }
}
