/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.plugin.test.helper.vo;

import java.io.FileNotFoundException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class CaseIConfigurationTest {

    private InputStream configurationFile;

    @Before
    public void setUp() {
        configurationFile = CaseIConfigurationTest.class.getResourceAsStream("/configuration-test.yml");
        assertNotNull(configurationFile);
    }

    @Test
    public void testReadCaseConfiguration() throws FileNotFoundException {
        Yaml yaml = new Yaml();
        CaseConfiguration caseConfiguration = yaml.loadAs(configurationFile, CaseConfiguration.class);
        assertNotNull(caseConfiguration);

        assertThat(caseConfiguration.getDependencies().size(), is(1));
    }
}
