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

package org.apache.skywalking.oap.server.core.analysis;

import java.lang.annotation.Annotation;
import java.util.*;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.worker.*;
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;
import org.apache.skywalking.oap.server.core.register.worker.InventoryStreamProcessor;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * @author peng-yongsheng
 */
public class StreamAnnotationListener implements AnnotationListener {

    private final ModuleDefineHolder moduleDefineHolder;
    private final List<Class> streamClassList;

    public StreamAnnotationListener(ModuleDefineHolder moduleDefineHolder) {
        this.moduleDefineHolder = moduleDefineHolder;
        this.streamClassList = new ArrayList<>(50);
    }

    @Override public Class<? extends Annotation> annotation() {
        return Stream.class;
    }

    @SuppressWarnings("unchecked")
    @Override public void notify(Class aClass) {
        if (aClass.isAnnotationPresent(Stream.class)) {
            streamClassList.add(aClass);
        } else {
            throw new UnexpectedException("Stream annotation listener could only parse the class present stream annotation.");
        }
    }

    public void init() {
        /**
         * The stream protocol use this list order to assign the ID,
         * which is used in across node communication. This order must be certain.
         */
        Collections.sort(streamClassList, new Comparator<Class>() {
            @Override public int compare(Class streamClass1, Class streamClass2) {
                return streamClass1.getName().compareTo(streamClass2.getName());
            }
        });

        streamClassList.forEach(streamClass -> {
            Stream stream = (Stream)streamClass.getAnnotation(Stream.class);

            if (stream.processor().equals(InventoryStreamProcessor.class)) {
                InventoryStreamProcessor.getInstance().create(moduleDefineHolder, stream, streamClass);
            } else if (stream.processor().equals(RecordStreamProcessor.class)) {
                RecordStreamProcessor.getInstance().create(moduleDefineHolder, stream, streamClass);
            } else if (stream.processor().equals(MetricsStreamProcessor.class)) {
                MetricsStreamProcessor.getInstance().create(moduleDefineHolder, stream, streamClass);
            } else if (stream.processor().equals(TopNStreamProcessor.class)) {
                TopNStreamProcessor.getInstance().create(moduleDefineHolder, stream, streamClass);
            } else {
                throw new UnexpectedException("Unknown stream processor.");
            }
        });
    }
}
