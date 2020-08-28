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

package org.apache.skywalking.apm.agent.core.plugin;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.plugin.exception.IllegalPluginDefineException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.skywalking.apm.agent.core.conf.Config.Plugin.EXCLUDE_PLUGINS;

public class PluginSelectorTest {

    List<PluginDefine> pluginDefines;
    PluginSelector selector;

    @Before
    public void prepare() throws IllegalPluginDefineException {
        pluginDefines = new ArrayList<>();
        selector = new PluginSelector();
        pluginDefines.add(PluginDefine.build("elasticsearch=elasticsearchClass"));
        pluginDefines.add(PluginDefine.build("mysql=mysqlClass"));
    }

    @Test
    public void selectDefaultTest() {
        EXCLUDE_PLUGINS = "";
        List<PluginDefine> select = selector.select(pluginDefines);
        Assert.assertEquals(2, select.size());
    }

    @Test
    public void selectNormalTest() {
        EXCLUDE_PLUGINS = "mysql";
        List<PluginDefine> plugins = selector.select(pluginDefines);
        Assert.assertEquals(1, plugins.size());
        Assert.assertEquals("elasticsearch", plugins.get(0).getName());
    }
}