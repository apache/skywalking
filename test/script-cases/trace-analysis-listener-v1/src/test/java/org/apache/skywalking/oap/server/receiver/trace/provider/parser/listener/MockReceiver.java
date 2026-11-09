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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.DispatcherDetectorListener;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;

/**
 * Mock receiver for testing.
 */
public class MockReceiver implements SourceReceiver {
    @Getter
    private List<ISource> receivedSources = new ArrayList<>();

    @Override
    public void receive(final ISource source) {
        receivedSources.add(source);
    }

    public void clear() {
        receivedSources.clear();
    }

    @Override
    public DispatcherDetectorListener getDispatcherDetectorListener() {
        throw new UnsupportedOperationException("No getDispatcherDetectorListener in mock receiver.");
    }
}
