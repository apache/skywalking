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

package org.apache.skywalking.oap.server.receiver.otel;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;

public interface Handler {
    static List<Handler> all() throws HandlerInitializationException {
        ClassPath classpath;
        try {
            classpath = ClassPath.from(Handler.class.getClassLoader());
        } catch (IOException e) {
            throw new HandlerInitializationException("failed to load handler classes", e);
        }
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive(Handler.class.getPackage().getName());
        List<Handler> result = new ArrayList<>();
        for (ClassPath.ClassInfo each : classes) {
            Class<?> c = each.load();
            if (Arrays.stream(c.getInterfaces()).anyMatch(interfaceClass -> interfaceClass.isAssignableFrom(Handler.class))) {
                try {
                    result.add((Handler) c.newInstance());
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new HandlerInitializationException("failed to get instances of handler classed", e);
                }
            }
        }
        return result;
    }

    String type();

    void active(List<String> enabledRules, MeterSystem service,
        GRPCHandlerRegister grpcHandlerRegister);

}
