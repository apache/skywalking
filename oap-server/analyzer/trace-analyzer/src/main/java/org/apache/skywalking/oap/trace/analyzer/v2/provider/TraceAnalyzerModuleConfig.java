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

package org.apache.skywalking.oap.trace.analyzer.v2.provider;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@EqualsAndHashCode(callSuper = false)
public class TraceAnalyzerModuleConfig extends ModuleConfig {
    @Getter
    @Setter
    private String talPath = "tal";

    /**
     * TAL rule files under {@link #talPath}, without extension, comma separated. Empty turns span analysis off
     * while keeping the module, which the receivers require.
     */
    @Getter
    @Setter
    private String talFiles = "otlp-general";

    /**
     * Seconds. Spans seen within this window are analyzed once, so an exporter retrying a batch after a transport
     * timeout does not count a request twice. 0 disables the check and allocates no set, which is what OTLP logs
     * do today; this is the switch, {@link #dedupCapacity} only bounds the set once it is on. Per OAP node.
     */
    @Getter
    @Setter
    private int dedupWindow = 0;

    /**
     * The most {@code (traceId, spanId)} pairs the dedup set holds on this OAP node while {@link #dedupWindow} is
     * above 0; ignored at 0. Sized as spans per second times the window, with headroom: a full set drops its
     * oldest entries before they expire and counts a late retry of one of them. The default matches the core's
     * {@code maxSizeOfNetworkAddressAlias} and costs about 150 bytes per entry when full.
     */
    @Getter
    @Setter
    private int dedupCapacity = 1000000;

    /**
     * The percentage of traces, by trace id, whose call pairs are paired, on the trace's owner node in a cluster and
     * on the node itself when it is alone, so an address is learned from a sample of its calls; both halves of a call
     * share the id, so a pair is never split. An address is learned once and then resolved from every node's cache,
     * which is why a sample is enough. 100 pairs every call, 0 none. An int because the module config loader sets no
     * double fields.
     */
    @Getter
    @Setter
    private int pairingSamplePercent = 25;

    public List<String> talFiles() {
        return Splitter.on(",").omitEmptyStrings().trimResults().splitToList(Strings.nullToEmpty(getTalFiles()));
    }
}
