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

package org.apache.skywalking.apm.plugin.jdbc;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SqlBodyBuilderTest {

    @Test
    public void testBuildWithEmptySqlBody() {
        String sql = new SqlBodyBuilder().build();
        assertThat(sql, is(""));
    }

    @Test
    public void testBuildWithDefaultLength() {
        String sql = new SqlBodyBuilder().setSqlBody("select * from dual").build();
        assertThat(sql, is("select * from dual"));
    }

    @Test
    public void testBuildWithMaxLength() {
        String sql = new SqlBodyBuilder().setSqlBody("select * from dual").setMaxLength(10).build();
        assertThat(sql, is("select * f..."));
    }
}