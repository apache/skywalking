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

package org.apache.skywalking.oap.server.core.analysis.record.annotation;

import java.lang.annotation.Annotation;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordProcess;
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public class RecordTypeListener implements AnnotationListener {

    private final ModuleManager moduleManager;

    public RecordTypeListener(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @Override public Class<? extends Annotation> annotation() {
        return RecordType.class;
    }

    @Override public void notify(Class aClass) {
        RecordProcess.INSTANCE.create(moduleManager, aClass);
    }
}
