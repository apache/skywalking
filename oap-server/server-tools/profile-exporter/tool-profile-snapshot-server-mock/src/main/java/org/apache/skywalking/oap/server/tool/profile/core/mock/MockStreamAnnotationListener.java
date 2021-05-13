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

package org.apache.skywalking.oap.server.tool.profile.core.mock;

import java.lang.annotation.Annotation;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamAnnotationListener;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * Mock from {@link StreamAnnotationListener}
 */
public class MockStreamAnnotationListener implements AnnotationListener {

    private final ModuleDefineHolder moduleDefineHolder;

    public MockStreamAnnotationListener(ModuleDefineHolder moduleDefineHolder) {
        this.moduleDefineHolder = moduleDefineHolder;
    }

    @Override
    public Class<? extends Annotation> annotation() {
        return Stream.class;
    }

    @Override
    public void notify(Class aClass) throws StorageException {
        if (aClass.isAnnotationPresent(Stream.class)) {
            Stream stream = (Stream) aClass.getAnnotation(Stream.class);

            if (stream.processor().equals(RecordStreamProcessor.class)) {
                RecordStreamProcessor.getInstance().create(moduleDefineHolder, stream, aClass);
            } else if (stream.processor().equals(NoneStreamProcessor.class)) {
                NoneStreamProcessor.getInstance().create(moduleDefineHolder, stream, aClass);
            }
        } else {
            throw new UnexpectedException(
                "Stream annotation listener could only parse the class present stream annotation.");
        }
    }
}
