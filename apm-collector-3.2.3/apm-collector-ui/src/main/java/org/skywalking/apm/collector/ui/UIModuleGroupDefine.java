/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui;

import org.skywalking.apm.collector.core.config.GroupConfigParser;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author peng-yongsheng
 */
public class UIModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "ui";
    private final UIModuleInstaller installer;

    public UIModuleGroupDefine() {
        installer = new UIModuleInstaller();
    }

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new UIModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return installer;
    }

    @Override public GroupConfigParser groupConfigParser() {
        return null;
    }
}
