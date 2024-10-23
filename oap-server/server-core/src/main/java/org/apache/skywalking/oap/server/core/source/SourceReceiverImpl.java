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

package org.apache.skywalking.oap.server.core.source;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.DispatcherDetectorListener;
import org.apache.skywalking.oap.server.core.analysis.DispatcherManager;
import org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager;

public class SourceReceiverImpl implements SourceReceiver {
    @Getter
    private final DispatcherManager dispatcherManager;

    @Getter
    private final SourceDecoratorManager sourceDecoratorManager;

    public SourceReceiverImpl() {
        this.dispatcherManager = new DispatcherManager();
        this.sourceDecoratorManager = new SourceDecoratorManager();
    }

    @Override
    public void receive(ISource source) {
        dispatcherManager.forward(source);
    }

    @Override
    public DispatcherDetectorListener getDispatcherDetectorListener() {
        return getDispatcherManager();
    }

    public void scan() throws IOException, InstantiationException, IllegalAccessException {
        ClassPath classpath = ClassPath.from(this.getClass().getClassLoader());
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");
        for (ClassPath.ClassInfo classInfo : classes) {
            Class<?> aClass = classInfo.load();
            sourceDecoratorManager.addIfAsSourceDecorator(aClass);
            dispatcherManager.addIfAsSourceDispatcher(aClass);
        }
    }
}
