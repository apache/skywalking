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

package org.apache.skywalking.oap.server.core.profile;

import org.apache.skywalking.oap.server.core.profile.analyze.ProfileAnalyzer;
import org.apache.skywalking.oap.server.core.query.entity.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.entity.ProfileStackElement;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ProfileAnalyzerTest {

    @Test
    public void testAnalyze() {
        final ProfileStackHolder dataHolder = loadYaml("thread-snapshot.yml", ProfileStackHolder.class);
        ProfileAnalyzation analyze = ProfileAnalyzer.analyze(dataHolder.getList());
        final ProfileAnalyzation verify = loadYaml("thread-snapshot-verify.yml", ProfileAnalyzation.class);

        assertSame(analyze, verify);
    }

    private <T> T loadYaml(String file, Class<T> cls) {
        InputStream expectedInputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(file);
        return new Yaml().loadAs(expectedInputStream, cls);
    }

    private void assertSame(ProfileAnalyzation verify, ProfileAnalyzation except) {
        assertNotNull(verify);
        assertNotNull(except);
        assertNotNull(verify.getStack());
        assertNotNull(except.getStack());
        assertEquals(verify.getStack().size(), except.getStack().size());

        for (int i = 0; i < verify.getStack().size(); i++) {
            assertElement(verify.getStack().get(i), except.getStack().get(i));
        }
    }

    private void assertElement(ProfileStackElement verify, ProfileStackElement except) {
        assertNotNull(verify);
        assertNotNull(except);

        assertEquals(verify.getCodeSignature(), except.getCodeSignature());
        assertEquals(verify.getDuration(), except.getDuration());
        assertEquals(verify.getDurationChildExcluded(), except.getDurationChildExcluded());
        assertEquals(verify.getCount(), except.getCount());
        assertEquals(CollectionUtils.isNotEmpty(verify.getChilds()), CollectionUtils.isNotEmpty(except.getChilds()));

        if (CollectionUtils.isNotEmpty(verify.getChilds())) {
            assertEquals(verify.getChilds().size(), except.getChilds().size());
            for (int i = 0; i < verify.getChilds().size(); i++) {
                assertElement(verify.getChilds().get(i), except.getChilds().get(i));
            }
        }
    }

}
