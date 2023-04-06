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

package org.apache.skywalking.oap.server.core.profiling.trace;

import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.type.ProfiledTraceSegments;
import org.apache.skywalking.oap.server.core.query.type.ProfiledSpan;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opentest4j.AssertionFailedError;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProfileTaskQueryServiceTest {

    @Mock
    private ModuleManager moduleManager;
    @Mock
    private CoreModuleConfig moduleConfig;
    @Mock
    private ModuleProviderHolder providerHolder;
    @Mock
    private CoreModuleProvider coreModuleProvider;
    @Mock
    private IComponentLibraryCatalogService catalogService;

    @BeforeEach
    public void setup() {
        when(moduleManager.find(anyString())).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(coreModuleProvider);
        when(coreModuleProvider.getService(IComponentLibraryCatalogService.class)).thenReturn(catalogService);
        when(catalogService.getComponentName(anyInt())).thenReturn("");
        when(moduleConfig.getMaxPageSizeOfQueryProfileSnapshot()).thenReturn(1);
        when(moduleConfig.getMaxSizeOfAnalyzeProfileSnapshot()).thenReturn(1);
    }

    @Test
    public void testBuildProfiledSegmentsList() {
        // all segment in same process
        validate(Arrays.asList(
            buildRecord("1B", "2A", RefType.CrossThread),
            buildRecord("2A", "", null),
            buildRecord("3C", "1B", RefType.CrossThread)
        ), Arrays.asList(
            Arrays.asList("2A", "1B", "3C")
        ));

        // segment with different process
        validate(Arrays.asList(
            buildRecord("A", "", null),
            buildRecord("B", "A", RefType.CrossThread),

            buildRecord("C", "B", RefType.CrossProcess),

            buildRecord("D", "Z", RefType.CrossThread)
        ), Arrays.asList(
            Arrays.asList("A", "B"),
            Arrays.asList("C"),
            Arrays.asList("D")
        ));
    }

    private void validate(List<SegmentRecord> records, List<List<String>> excepted) {
        final ProfileTaskQueryService profileTaskQueryService = new ProfileTaskQueryService(moduleManager, moduleConfig);
        final List<ProfiledTraceSegments> result = profileTaskQueryService.buildProfiledSegmentsList(records, records.stream().map(SegmentRecord::getSegmentId).collect(Collectors.toList()));
        assertEquals(result.size(), excepted.size(), "result size not same");
        for (List<String> exceptedSegments : excepted) {
            boolean found = false;
            for (ProfiledTraceSegments segments : result) {
                if (segments.getSpans().stream().map(ProfiledSpan::getSegmentId).collect(Collectors.toList()).equals(exceptedSegments)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new AssertionFailedError("cannot find any matches result of {}, all actual data: {}",
                    exceptedSegments, result.stream().map(segments -> segments.getSpans().stream().map(ProfiledSpan::getSegmentId).collect(Collectors.toList())).collect(Collectors.toList()));
            }
        }
    }

    private SegmentRecord buildRecord(String segmentId, String parentSegmentId, RefType refType) {
        final SegmentRecord record = new SegmentRecord();
        record.setSegmentId(segmentId);
        final String testServiceId = IDManager.ServiceID.buildId("test", true);
        record.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(testServiceId, "test"));
        record.setEndpointId(IDManager.EndpointID.buildId(testServiceId, "test"));
        final SegmentObject.Builder builder = SegmentObject.newBuilder();
        builder.setTraceSegmentId(segmentId);
        final SpanObject.Builder firstSpan = SpanObject.newBuilder();
        if (StringUtil.isNotEmpty(parentSegmentId)) {
            firstSpan.addRefs(SegmentReference.newBuilder()
                .setParentTraceSegmentId(parentSegmentId)
                .setRefType(refType).build());
        }
        builder.addSpans(firstSpan.build());
        record.setDataBinary(builder.build().toByteArray());
        return record;
    }
}
