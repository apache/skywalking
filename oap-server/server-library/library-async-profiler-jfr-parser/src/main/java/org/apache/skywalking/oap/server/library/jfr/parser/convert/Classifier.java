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

/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.convert;

import org.apache.skywalking.oap.server.library.jfr.parser.type.StackTrace;

import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_C1_COMPILED;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_CPP;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_INLINED;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_INTERPRETED;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_JIT_COMPILED;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_NATIVE;

abstract class Classifier {

    enum Category {
        GC("[gc]", TYPE_CPP),
        JIT("[jit]", TYPE_CPP),
        VM("[vm]", TYPE_CPP),
        VTABLE_STUBS("[vtable_stubs]", TYPE_NATIVE),
        NATIVE("[native]", TYPE_NATIVE),
        INTERPRETER("[Interpreter]", TYPE_NATIVE),
        C1_COMP("[c1_comp]", TYPE_C1_COMPILED),
        C2_COMP("[c2_comp]", TYPE_INLINED),
        ADAPTER("[c2i_adapter]", TYPE_INLINED),
        CLASS_INIT("[class_init]", TYPE_CPP),
        CLASS_LOAD("[class_load]", TYPE_CPP),
        CLASS_RESOLVE("[class_resolve]", TYPE_CPP),
        CLASS_VERIFY("[class_verify]", TYPE_CPP),
        LAMBDA_INIT("[lambda_init]", TYPE_CPP);

        final String title;
        final byte type;

        Category(String title, byte type) {
            this.title = title;
            this.type = type;
        }
    }

    public Category getCategory(StackTrace stackTrace) {
        long[] methods = stackTrace.methods;
        byte[] types = stackTrace.types;

        Category category;
        if ((category = detectGcJit(methods, types)) == null &&
                (category = detectClassLoading(methods, types)) == null) {
            category = detectOther(methods, types);
        }
        return category;
    }

    private Category detectGcJit(long[] methods, byte[] types) {
        boolean vmThread = false;
        for (int i = types.length; --i >= 0; ) {
            if (types[i] == TYPE_CPP) {
                switch (getMethodName(methods[i], types[i])) {
                    case "CompileBroker::compiler_thread_loop":
                        return Category.JIT;
                    case "GCTaskThread::run":
                    case "WorkerThread::run":
                        return Category.GC;
                    case "java_start":
                    case "thread_native_entry":
                        vmThread = true;
                        break;
                }
            } else if (types[i] != TYPE_NATIVE) {
                break;
            }
        }
        return vmThread ? Category.VM : null;
    }

    private Category detectClassLoading(long[] methods, byte[] types) {
        for (int i = 0; i < methods.length; i++) {
            String methodName = getMethodName(methods[i], types[i]);
            if (methodName.equals("Verifier::verify")) {
                return Category.CLASS_VERIFY;
            } else if (methodName.startsWith("InstanceKlass::initialize")) {
                return Category.CLASS_INIT;
            } else if (methodName.startsWith("LinkResolver::") ||
                    methodName.startsWith("InterpreterRuntime::resolve") ||
                    methodName.startsWith("SystemDictionary::resolve")) {
                return Category.CLASS_RESOLVE;
            } else if (methodName.endsWith("ClassLoader.loadClass")) {
                return Category.CLASS_LOAD;
            } else if (methodName.endsWith("LambdaMetafactory.metafactory") ||
                    methodName.endsWith("LambdaMetafactory.altMetafactory")) {
                return Category.LAMBDA_INIT;
            } else if (methodName.endsWith("table stub")) {
                return Category.VTABLE_STUBS;
            } else if (methodName.equals("Interpreter")) {
                return Category.INTERPRETER;
            } else if (methodName.startsWith("I2C/C2I")) {
                return i + 1 < types.length && types[i + 1] == TYPE_INTERPRETED ? Category.INTERPRETER : Category.ADAPTER;
            }
        }
        return null;
    }

    private Category detectOther(long[] methods, byte[] types) {
        boolean inJava = true;
        for (int i = 0; i < types.length; i++) {
            switch (types[i]) {
                case TYPE_INTERPRETED:
                    return inJava ? Category.INTERPRETER : Category.NATIVE;
                case TYPE_JIT_COMPILED:
                    return inJava ? Category.C2_COMP : Category.NATIVE;
                case TYPE_INLINED:
                    inJava = true;
                    break;
                case TYPE_NATIVE: {
                    String methodName = getMethodName(methods[i], types[i]);
                    if (methodName.startsWith("JVM_") || methodName.startsWith("Unsafe_") ||
                            methodName.startsWith("MHN_") || methodName.startsWith("jni_")) {
                        return Category.VM;
                    }
                    switch (methodName) {
                        case "call_stub":
                        case "deoptimization":
                        case "unknown_Java":
                        case "not_walkable_Java":
                        case "InlineCacheBuffer":
                            return Category.VM;
                    }
                    if (methodName.endsWith("_arraycopy") || methodName.contains("pthread_cond")) {
                        break;
                    }
                    inJava = false;
                    break;
                }
                case TYPE_CPP: {
                    String methodName = getMethodName(methods[i], types[i]);
                    if (methodName.startsWith("Runtime1::")) {
                        return Category.C1_COMP;
                    }
                    break;
                }
                case TYPE_C1_COMPILED:
                    return inJava ? Category.C1_COMP : Category.NATIVE;
            }
        }
        return Category.NATIVE;
    }

    protected abstract String getMethodName(long method, byte type);
}
