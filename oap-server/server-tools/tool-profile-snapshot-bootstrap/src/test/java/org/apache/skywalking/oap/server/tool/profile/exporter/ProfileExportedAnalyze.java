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

package org.apache.skywalking.oap.server.tool.profile.exporter;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.profile.ThreadSnapshot;
import org.apache.skywalking.oap.server.core.query.entity.Span;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class ProfileExportedAnalyze {

    public static void main(String[] args) throws IOException {
        // input
        final File basicInfoFile = new File("/Users/liuhan/Documents/idea_workspace/lagou_new_workspace/skywalking-liuhan/skywalking/e2e-profile/e2e-profile-test-runner/apache-skywalking-apm-bin-es7/work/basic.yml");
        final File snapshotFile = new File("/Users/liuhan/Documents/idea_workspace/lagou_new_workspace/skywalking-liuhan/skywalking/e2e-profile/e2e-profile-test-runner/apache-skywalking-apm-bin-es7/work/snapshot.data");
        String profiledSpanName = "/e2e/users";

        // parsing data
        final ProfiledBasicInfo basicInfo = ProfiledBasicInfo.parseFormFile(basicInfoFile);
        final List<Span> sameNameSpans = basicInfo.getProfiledSegmentSpans().stream().filter(s -> Objects.equals(s.getEndpointName(), profiledSpanName)).collect(Collectors.toList());
        final Span span = sameNameSpans.get(0);

        final List<ThreadSnapshot> snapshots = ProfileSnapshotDumper.parseFromFileWithTimeRange(snapshotFile, span.getStartTime(), span.getEndTime());
        log.info("Total found snapshot count: {}", snapshots.size());
    }

}
