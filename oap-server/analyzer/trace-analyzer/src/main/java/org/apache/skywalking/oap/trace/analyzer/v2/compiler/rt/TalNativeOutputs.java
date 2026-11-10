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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt;

import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;

/**
 * What the native input's generated output statements call, beside the sources generated code builds itself. Not a
 * function a rule calls.
 */
public final class TalNativeOutputs {
    private TalNativeOutputs() {
    }

    /**
     * {@code writeNetworkAlias <address>} — the alias row an entry span's cross-process reference names, sent now, as
     * {@code NetworkAddressAliasMappingListener} sends it: the segment's service and instance, formatted in that
     * order, as what the address represents, at the minute of the span's start. The dispatcher and the cache reload
     * do the rest.
     *
     * @param h       the run, whose input is the native span
     * @param address the address the caller dialed, as the rule formatted it
     */
    public static void writeNetworkAlias(final TalRuntimeHelper h, final String address) {
        final SpanObject span = (SpanObject) h.input();
        final SegmentObject segment = h.ctx().getNativeSegment().getSegmentObject();
        final NamingControl naming = h.spec().getNamingControl();
        final NetworkAddressAliasSetup setup = new NetworkAddressAliasSetup();
        setup.setAddress(address);
        setup.setRepresentService(naming.formatServiceName(segment.getService()));
        setup.setRepresentServiceNormal(true);
        setup.setRepresentServiceInstance(naming.formatInstanceName(segment.getServiceInstance()));
        setup.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));
        h.spec().getSourceReceiver().receive(setup);
    }
}
