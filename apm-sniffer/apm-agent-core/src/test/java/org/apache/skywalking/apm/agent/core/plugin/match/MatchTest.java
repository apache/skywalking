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

package org.apache.skywalking.apm.agent.core.plugin.match;

import net.bytebuddy.description.type.TypeDescription;
import org.apache.skywalking.apm.agent.core.plugin.match.logical.LogicalMatchOperation;
import org.junit.Assert;
import org.junit.Test;

import static org.apache.skywalking.apm.agent.core.plugin.match.ClassAnnotationMatch.byClassAnnotationMatch;
import static org.apache.skywalking.apm.agent.core.plugin.match.RegexMatch.byRegexMatch;

public class MatchTest {
    private static final String[] REGEX = new String[] {
        ".*Service.*",
        ".*Repository.*"
    };

    @Test
    public void testRegexMatch() {
        RegexMatch regexMatch = byRegexMatch(REGEX);
        TypeDescription typeDefinition = TypeDescription.ForLoadedType.of(TestService.class);
        Assert.assertTrue(regexMatch.isMatch(typeDefinition));
        typeDefinition = TypeDescription.ForLoadedType.of(TestDao.class);
        Assert.assertFalse(regexMatch.isMatch(typeDefinition));
        typeDefinition = TypeDescription.ForLoadedType.of(TestRepository.class);
        Assert.assertTrue(regexMatch.isMatch(typeDefinition));
    }

    @Test
    public void testAnnotationMatch() {
        ClassAnnotationMatch classAnnotationMatch = byClassAnnotationMatch(MatchTestAnnotation.class.getName());
        TypeDescription typeDefinition = TypeDescription.ForLoadedType.of(TestService.class);
        Assert.assertFalse(classAnnotationMatch.isMatch(typeDefinition));
        typeDefinition = TypeDescription.ForLoadedType.of(TestDao.class);
        Assert.assertTrue(classAnnotationMatch.isMatch(typeDefinition));
        typeDefinition = TypeDescription.ForLoadedType.of(TestRepository.class);
        Assert.assertTrue(classAnnotationMatch.isMatch(typeDefinition));
    }

    @Test
    public void testLogicalMatchOperation() {
        IndirectMatch match = LogicalMatchOperation.and(
            byRegexMatch(REGEX),
            byClassAnnotationMatch(MatchTestAnnotation.class.getName())
        );
        TypeDescription typeDefinition = TypeDescription.ForLoadedType.of(TestService.class);
        Assert.assertFalse(match.isMatch(typeDefinition));
        typeDefinition = TypeDescription.ForLoadedType.of(TestDao.class);
        Assert.assertFalse(match.isMatch(typeDefinition));
        typeDefinition = TypeDescription.ForLoadedType.of(TestRepository.class);
        Assert.assertTrue(match.isMatch(typeDefinition));
    }

    public static class TestService {

    }

    @MatchTestAnnotation
    public static class TestDao {

    }

    @MatchTestAnnotation
    public static class TestRepository {

    }
}  