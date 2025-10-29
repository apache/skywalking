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

package org.apache.skywalking.oap.server.library.pprof.type;

import com.google.perftools.profiles.ProfileProto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.skywalking.oap.server.library.pprof.parser.PprofParser;

@Data
@NoArgsConstructor
@AllArgsConstructor
class RawFrameTree {
    private long locationId;
    private long total;
    private long self;
    private final Map<Long, RawFrameTree> children = new HashMap<>();
}

public class FrameTreeBuilder {
    private final ProfileProto.Profile profile;
    private final RawFrameTree root;

    public FrameTreeBuilder(ProfileProto.Profile profile) {
        this.profile = profile;
        this.root = new RawFrameTree(0, 0, 0);
    }

    private FrameTree parseTree(RawFrameTree rawTree) {
        FrameTree tree = new FrameTree(getSignature(rawFrameTreeGetLocationId(rawTree)), rawTree.getTotal(), rawTree.getSelf());
        for (RawFrameTree rawChild : rawTree.getChildren().values()) {
            FrameTree child = parseTree(rawChild);
            tree.getChildren().add(child);
        }
        return tree;
    }

    // Small indirection to keep minimal change footprint while delegating signature resolution
    private long rawFrameTreeGetLocationId(RawFrameTree rawTree) {
        return rawTree.getLocationId();
    }

    private String getSignature(long locationId) {
        return PprofParser.resolveSignature(locationId, profile);
    }

    public FrameTree build() {
        for (ProfileProto.Sample sample : profile.getSampleList()) {
            mergeSample(sample);
        }
        return parseTree(this.root);
    }

    private void mergeSample(ProfileProto.Sample sample) {
        // merge sample data
        Map<Long, RawFrameTree> children = root.getChildren();
        List<Long> locationIdList = new ArrayList<>(sample.getLocationIdList());
        // from root to leaf
        Collections.reverse(locationIdList);
        int size = locationIdList.size();
        for (int i = 0; i < size; i++) {
            boolean isEnd = i == size - 1;
            long locationId = locationIdList.get(i);
            if (children.containsKey(locationId)) {
                // if the child exists, merge the sample data
                RawFrameTree child = children.get(locationId);
                child.setTotal(child.getTotal() + 1);
                child.setSelf(child.getSelf() + (isEnd ? 1 : 0));
                children = child.getChildren();
            } else {
                // if the child does not exist, create a new child
                RawFrameTree child = new RawFrameTree(locationId, 1, (isEnd ? 1 : 0));
                children.put(locationId, child);
                children = child.getChildren();
            }
        }
        root.setTotal(root.getTotal() + 1);
    }
} 