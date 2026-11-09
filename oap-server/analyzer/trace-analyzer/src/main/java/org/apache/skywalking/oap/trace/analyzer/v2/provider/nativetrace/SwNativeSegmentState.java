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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace;

import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;

/**
 * One segment run's state beside the segment the agent sent: what the runner derives from the whole segment before the
 * first rule runs, as {@code SegmentAnalysisListener.parseSegment} derived it into its fields. One per segment run,
 * shared by the runs of every span of it; the rules read it through the native input's segment functions.
 */
@Getter
public class SwNativeSegmentState {
    private final SegmentObject segmentObject;
    @Setter
    private long startTime;
    @Setter
    private long endTime;
    @Setter
    private int latency;
    @Setter
    private boolean error;
    @Setter
    private boolean sampled;
    private List<Tag> tags = Collections.emptyList();

    public SwNativeSegmentState(final SegmentObject segmentObject) {
        this.segmentObject = segmentObject;
    }

    /**
     * @param tags the searchable tags the segment record stores, read-only from here on
     */
    void setTags(final List<Tag> tags) {
        this.tags = Collections.unmodifiableList(tags);
    }
}
