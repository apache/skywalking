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

package org.apache.skywalking.oap.server.core.alarm.provider;

import java.io.*;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.*;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;

public class AlarmModuleProvider extends ModuleProvider {
    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return AlarmModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return new AlarmSettings();
    }

    @Override public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        Reader applicationReader;
        try {
            applicationReader = ResourceUtils.read("alarm-settings.yml");
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("can't load alarm-settings.yml", e);
        }
        RulesReader reader = new RulesReader(applicationReader);
        Rules rules = reader.readRules();
        NotifyHandler notifyHandler = new NotifyHandler(rules);
        notifyHandler.init(new AlarmStandardPersistence());
        this.registerServiceImplementation(IndicatorNotify.class, notifyHandler);
    }

    @Override public void start() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
