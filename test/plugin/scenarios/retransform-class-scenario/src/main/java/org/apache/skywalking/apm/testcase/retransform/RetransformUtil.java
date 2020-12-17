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

package org.apache.skywalking.apm.testcase.retransform;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.retransform.controller.CaseController;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class RetransformUtil {

    private static final Logger LOGGER = LogManager.getLogger(RetransformUtil.class);
    public static final String RETRANSFORMING_TAG = "_retransforming_";
    public static final String RETRANSFORM_VALUE = "hello_from_agent";

    public static void doRetransform() {
        Instrumentation instrumentation = ByteBuddyAgent.install();

        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if (className.endsWith("CaseController")) {
                    //replace '_retransforming_' with 'hello_from_agent', both length is 16
                    byte[] bytes = RETRANSFORMING_TAG.getBytes();
                    int offset = indexOf(classfileBuffer, bytes);
                    if (offset != -1) {
                        byte[] replacingBytes = RETRANSFORM_VALUE.getBytes();
                        System.arraycopy(replacingBytes, 0, classfileBuffer, offset, replacingBytes.length);
                    }
                    return classfileBuffer;
                }
                return null;
            }
        };

        try {
            instrumentation.addTransformer(transformer, true);
            try {
                instrumentation.retransformClasses(CaseController.class);
                LOGGER.info("retransform classes success");
            } catch (Throwable e) {
                LOGGER.error("retransform classes failure", e);
            }

        } finally {
            instrumentation.removeTransformer(transformer);
        }

    }

    private static int indexOf(byte[] outerArray, byte[] smallerArray) {
        for (int i = 0; i < outerArray.length - smallerArray.length + 1; ++i) {
            boolean found = true;
            for (int j = 0; j < smallerArray.length; ++j) {
                if (outerArray[i + j] != smallerArray[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
    }
}
