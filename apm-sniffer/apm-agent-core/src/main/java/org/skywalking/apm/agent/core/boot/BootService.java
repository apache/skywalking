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

package org.skywalking.apm.agent.core.boot;

/**
 * The <code>BootService</code> is an interface to all remote, which need to boot when plugin mechanism begins to
 * work.
 * {@link #boot()} will be called when <code>BootService</code> start up.
 *
 * @author wusheng
 */
public interface BootService {
    void beforeBoot() throws Throwable;

    void boot() throws Throwable;

    void afterBoot() throws Throwable;

    void shutdown() throws Throwable;
}
