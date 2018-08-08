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

package org.apache.skywalking.oap.server.core.worker.annotation;

import java.lang.annotation.Annotation;
import java.util.*;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class WorkerAnnotationListener implements AnnotationListener {

    private static final Logger logger = LoggerFactory.getLogger(WorkerAnnotationListener.class);

    @Getter private final List<Class> workerClasses;

    public WorkerAnnotationListener() {
        this.workerClasses = new LinkedList<>();
    }

    @Override public Class<? extends Annotation> annotation() {
        return Worker.class;
    }

    @Override public void ownerClass(Class aClass) {
        logger.info("The owner class of worker annotation, class name: {}", aClass.getName());

        workerClasses.add(aClass);
    }
}
