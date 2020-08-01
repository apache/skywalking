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

package org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler;

import com.google.common.collect.Lists;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.DispatcherDetectorListener;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.junit.rules.Verifier;

public abstract class SourceReceiverRule extends Verifier implements SourceReceiver {
    private final List<Source> sourceList = Lists.newArrayList();

    @Override
    public void receive(final Source source) {
        sourceList.add(source);
    }

    @Override
    public DispatcherDetectorListener getDispatcherDetectorListener() {
        return null;
    }

    protected void verify() throws Throwable {
        verify(sourceList);
    }

    protected abstract void verify(List<Source> sourceList) throws Throwable;

}
