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

package org.apache.skywalking.oap.server.core.storage.model;

import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ModelColumnTest {
    @Mock
    private Column c;

    @Before
    public void before() {
        when(c.name()).thenReturn("abc");
    }

    @Test
    public void testColumnDefine() {
        ModelColumn column = new ModelColumn(new ColumnName(c), byte[].class, byte[].class,
                                             false, false, true, 0,
                                             new SQLDatabaseExtension(),
                                             new ElasticSearchExtension(
                                                 ElasticSearch.MatchQuery.AnalyzerType.OAP_ANALYZER, false, false),
                                             new BanyanDBExtension(-1, false, true, BanyanDB.IndexRule.IndexType.INVERTED, false)
        );
        Assert.assertEquals(true, column.isStorageOnly());
        Assert.assertEquals("abc", column.getColumnName().getName());

        column = new ModelColumn(new ColumnName(c), DataTable.class, DataTable.class,
                                 false, false, true, 200,
                                 new SQLDatabaseExtension(),
                                 new ElasticSearchExtension(ElasticSearch.MatchQuery.AnalyzerType.OAP_ANALYZER, false, false),
                                 new BanyanDBExtension(-1, false, true, BanyanDB.IndexRule.IndexType.INVERTED, false)
        );
        Assert.assertEquals(true, column.isStorageOnly());
        Assert.assertEquals("abc", column.getColumnName().getName());
        Assert.assertEquals(200, column.getLength());

        column = new ModelColumn(new ColumnName(c), String.class, String.class,
                                 false, false, true, 200,
                                 new SQLDatabaseExtension(),
                                 new ElasticSearchExtension(ElasticSearch.MatchQuery.AnalyzerType.OAP_ANALYZER, false, false),
                                 new BanyanDBExtension(-1, false, true, BanyanDB.IndexRule.IndexType.INVERTED, false)
        );
        Assert.assertEquals(false, column.isStorageOnly());
        Assert.assertEquals("abc", column.getColumnName().getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConflictDefinition() {
        ModelColumn column = new ModelColumn(new ColumnName(c), String.class, String.class,
                                             true, false, true, 200,
                                             new SQLDatabaseExtension(),
                                             new ElasticSearchExtension(
                                                 ElasticSearch.MatchQuery.AnalyzerType.OAP_ANALYZER, false, false),
                                             new BanyanDBExtension(-1, false, true, BanyanDB.IndexRule.IndexType.INVERTED, false)
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConflictDefinitionIndexOnly() {
        ModelColumn column = new ModelColumn(new ColumnName(c), String.class, String.class,
                                             true, true, false, 200,
                                             new SQLDatabaseExtension(),
                                             new ElasticSearchExtension(
                                                 ElasticSearch.MatchQuery.AnalyzerType.OAP_ANALYZER, false, false),
                                             new BanyanDBExtension(-1, false, true, BanyanDB.IndexRule.IndexType.INVERTED, false)
        );
    }
}
