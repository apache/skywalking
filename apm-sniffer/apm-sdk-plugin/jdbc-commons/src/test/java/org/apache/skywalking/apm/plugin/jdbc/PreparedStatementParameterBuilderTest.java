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
import static org.hamcrest.MatcherAssert.assertThat;

public class PreparedStatementParameterBuilderTest {
    private static Object[] PARAMETERS = new Object[]{"test", 1234};
    private static final String EMPTY_LIST = "[]";
    private PreparedStatementParameterBuilder builder;

    @Test
    public void testDefaultBuilder() {
        builder = new PreparedStatementParameterBuilder();
        assertThat(builder.build(), is(EMPTY_LIST));
    }

    @Test
    public void testNullParameters() {
        builder = new PreparedStatementParameterBuilder();
        builder.setParameters(null);
        assertThat(builder.build(), is(EMPTY_LIST));
    }

    @Test
    public void testMaxIndex() {
        builder = new PreparedStatementParameterBuilder();
        Object[] parameters = new Object[]{
                "test",
                1234,
                "abcd"
        };
        builder.setParameters(parameters).setMaxIndex(2);
        assertThat(builder.build(), is("[test,1234]"));
    }

    @Test
    public void testOutOfRangeOfMaxIndex() {
        builder = new PreparedStatementParameterBuilder();
        builder.setParameters(PARAMETERS);
        builder.setMaxIndex(3);
        assertThat(builder.build(), is("[test,1234]"));

        builder.setMaxIndex(0);
        assertThat(builder.build(), is(EMPTY_LIST));

        builder.setMaxIndex(-1);
        assertThat(builder.build(), is(EMPTY_LIST));
    }

    @Test
    public void testParametersString() {
        JDBCPluginConfig.Plugin.JDBC.SQL_PARAMETERS_MAX_LENGTH = 0;
        builder = new PreparedStatementParameterBuilder();
        Object[] parameters = new Object[]{
                "",
                1234,
                10.0,
                null,
                'c',
                true,
                0x100
        };
        builder.setParameters(parameters);
        assertThat(builder.build(), is("[,1234,10.0,null,c,true,256]"));
    }

    @Test
    public void testMaxLength() {
        builder = new PreparedStatementParameterBuilder();
        builder.setParameters(PARAMETERS);
        JDBCPluginConfig.Plugin.JDBC.SQL_PARAMETERS_MAX_LENGTH = 10;
        assertThat(builder.build(), is("[test,1234..."));
    }

    @Test
    public void testMaxLengthZero() {
        builder = new PreparedStatementParameterBuilder();
        builder.setParameters(PARAMETERS);
        JDBCPluginConfig.Plugin.JDBC.SQL_PARAMETERS_MAX_LENGTH = 0;
        assertThat(builder.build(), is("[test,1234]"));
    }

    @Test
    public void testMaxLengthOutOfRange() {
        builder = new PreparedStatementParameterBuilder();
        builder.setParameters(PARAMETERS);
        JDBCPluginConfig.Plugin.JDBC.SQL_PARAMETERS_MAX_LENGTH = 20;
        assertThat(builder.build(), is("[test,1234]"));
    }
}
