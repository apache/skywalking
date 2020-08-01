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

package org.apache.skywalking.apm.agent.core.jvm.cpu;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * getProcessCpuTime method on OperatingSystemMXBean
 * for both oracle/sun jvm: com.sun.management.OperatingSystemMXBean . 
 * for IBM jvm: com.ibm.lang.management.OperatingSystemMXBean.
 */
public class JmxCpuAccessor extends CPUMetricsAccessor {
    private static final String CLASS_MXBEAN_NAME = "OperatingSystemMXBean";
    private static final String GET_PROCESS_CPU_TIME = "getProcessCpuTime";

    private final OperatingSystemMXBean osMBean;
    private final Method cpuTimeMethod;

    public JmxCpuAccessor(int cpuCoreNum) {
        super(cpuCoreNum);
        this.osMBean = ManagementFactory.getOperatingSystemMXBean();
        try {
            Class<?> mxCls = null;
            Class<?> tmpCls = osMBean.getClass();
            int superCount = 0;
            while (superCount++ < 3 && mxCls == null) {
                if (!CLASS_MXBEAN_NAME.equals(tmpCls.getSimpleName())) {
                    final Class<?>[] interfaces = tmpCls.getInterfaces();
                    for (Class<?> intf : interfaces) {
                        if (intf.getName().equals(OperatingSystemMXBean.class.getName())) {
                            continue;
                        }
                        if (CLASS_MXBEAN_NAME.equals(intf.getSimpleName())) {
                            mxCls = intf;
                            break;
                        }
                    }
                    if (mxCls == null) {
                        tmpCls = tmpCls.getSuperclass();
                        continue;
                    } else {
                        break;
                    }
                } else {
                    mxCls = tmpCls;
                    break;
                }
            }
            cpuTimeMethod = mxCls.getDeclaredMethod(GET_PROCESS_CPU_TIME);
        } catch (SecurityException e) {
            throw new IllegalStateException(e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        this.init();
    }

    @Override
    protected long getCpuTime() {
        try {
            return (Long) cpuTimeMethod.invoke(osMBean);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }
}
