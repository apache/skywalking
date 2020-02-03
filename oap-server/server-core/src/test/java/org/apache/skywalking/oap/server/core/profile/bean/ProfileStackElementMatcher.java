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
package org.apache.skywalking.oap.server.core.profile.bean;

import lombok.Data;
import org.apache.skywalking.oap.server.core.query.entity.ProfileStackElement;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.junit.Assert;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@Data
public class ProfileStackElementMatcher {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\:(\\d+)");

    private String code;
    private String duration;
    private int count;
    private List<ProfileStackElementMatcher> children;

    public void verify(ProfileStackElement element) {
        // analyze duration
        Matcher durationInfo = DURATION_PATTERN.matcher(duration);
        Assert.assertTrue("duration field pattern not match", durationInfo.find());
        int duration = Integer.parseInt(durationInfo.group(1));
        int durationExcludeChild = Integer.parseInt(durationInfo.group(2));

        // assert
        assertEquals(code, element.getCodeSignature());
        assertEquals(duration, element.getDuration());
        assertEquals(durationExcludeChild, element.getDurationChildExcluded());
        assertEquals(count, element.getCount());

        if (CollectionUtils.isEmpty(children)) {
            children = Collections.emptyList();
        }
        if (CollectionUtils.isEmpty(element.getChildren())) {
            element.setChildren(Collections.emptyList());
        }
        assertEquals(children.size(), element.getChildren().size());

        // children code signature not sorted, need sort it, then verify
        Collections.sort(children, Comparator.comparing(c -> c.code));
        Collections.sort(element.getChildren(), Comparator.comparing(c -> c.getCodeSignature()));

        for (int i = 0; i < children.size(); i++) {
            children.get(i).verify(element.getChildren().get(i));
        }

    }

}
