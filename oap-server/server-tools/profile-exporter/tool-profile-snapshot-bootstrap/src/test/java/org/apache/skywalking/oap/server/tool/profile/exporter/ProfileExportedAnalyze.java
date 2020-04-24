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
import org.apache.skywalking.apm.network.language.profile.v3.ThreadSnapshot;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.profile.analyze.ProfileAnalyzer;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackElement;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackTree;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class ProfileExportedAnalyze {

    public static void main(String[] args) throws IOException {
        // input
        final File basicInfoFile = new File("basic.yml");
        final File snapshotFile = new File("snapshot.data");
        String profiledSpanName = "";
        boolean includeChildren = true;

        // parsing data
        final ProfiledBasicInfo basicInfo = ProfiledBasicInfo.parseFormFile(basicInfoFile);
        final List<Span> sameNameSpans = basicInfo.getProfiledSegmentSpans().stream().filter(s -> Objects.equals(s.getEndpointName(), profiledSpanName)).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(sameNameSpans)) {
            log.warn("Cannot found same name span:{}", profiledSpanName);
            return;
        }
        final Span span = sameNameSpans.get(0);

        // build time ranges
        final List<ProfileAnalyzeTimeRange> timeRanges = buildTimeRanges(basicInfo, span, includeChildren);
        final List<ThreadSnapshot> snapshots = ProfileSnapshotDumper.parseFromFileWithTimeRange(snapshotFile, timeRanges);
        log.info("Total found snapshot count: {}", snapshots.size());

        // analyze and print
        final ProfileAnalyzer profileAnalyzer = new Analyzer(snapshots);
        final ProfileAnalyzation analyzation = profileAnalyzer.analyze(null, timeRanges);
        printAnalyzation(analyzation);

    }

    private static void printAnalyzation(ProfileAnalyzation analyzation) {
        for (int i = 0; i < analyzation.getTrees().size(); i++) {
            log.info("--------------------");
            log.info("tree: {}", i);
            log.info("--------------------");

            final ProfileStackTree tree = analyzation.getTrees().get(i);
            final List<ProfileStackElement> elements = tree.getElements();

            printElements(elements, 0, 0);
        }
    }

    private static void printElements(List<ProfileStackElement> elements, int depth, int parentId) {
        final StringBuilder depthPrefix = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            depthPrefix.append(" |");
        }
        depthPrefix.append("\\-");

        for (ProfileStackElement element : elements) {
            if (element.getParentId() != parentId) {
                continue;
            }

            log.info("{} {}: [count:{}], [duration:{}:{}]", depthPrefix, element.getCodeSignature(), element.getCount(), element.getDuration(), element.getDurationChildExcluded());
            printElements(elements, depth + 1, element.getId());
        }
    }

    private static List<ProfileAnalyzeTimeRange> buildTimeRanges(ProfiledBasicInfo basicInfo, Span currentSpan, boolean includeChildren) {
        if (includeChildren) {
            final ProfileAnalyzeTimeRange range = new ProfileAnalyzeTimeRange();
            range.setStart(currentSpan.getStartTime());
            range.setEnd(currentSpan.getEndTime());
            return Collections.singletonList(range);
        }

        // find children spans
        final List<Span> childrenSpans = basicInfo.getProfiledSegmentSpans().stream()
                .filter(s -> s.getParentSpanId() == currentSpan.getSpanId())
                .sorted(Comparator.comparingLong(Span::getStartTime))
                .collect(Collectors.toList());

        final ArrayList<ProfileAnalyzeTimeRange> ranges = new ArrayList<>();
        long startTime = currentSpan.getStartTime();
        long endTime = currentSpan.getStartTime();
        for (Span span : childrenSpans) {
            if (span.getStartTime() > startTime) {
                final ProfileAnalyzeTimeRange range = new ProfileAnalyzeTimeRange();
                range.setStart(startTime);
                range.setEnd(span.getStartTime() - 1);
                ranges.add(range);
            }

            startTime = span.getEndTime();
            endTime = span.getEndTime();
        }

        // add last range
        if (endTime != currentSpan.getEndTime()) {
            final ProfileAnalyzeTimeRange range = new ProfileAnalyzeTimeRange();
            range.setStart(endTime);
            range.setEnd(currentSpan.getEndTime());
            ranges.add(range);
        }

        return ranges;
    }

    private static class Analyzer extends ProfileAnalyzer {

        private final IProfileThreadSnapshotQueryDAO dao;

        public Analyzer(List<ThreadSnapshot> snapshots) {
            super(null, new CoreModuleConfig().getMaxPageSizeOfQueryProfileSnapshot(), new CoreModuleConfig().getMaxSizeOfAnalyzeProfileSnapshot());
            this.dao = new ProfileAnalyzeSnapshotDAO(snapshots);
        }

        @Override
        protected IProfileThreadSnapshotQueryDAO getProfileThreadSnapshotQueryDAO() {
            return dao;
        }
    }

}
