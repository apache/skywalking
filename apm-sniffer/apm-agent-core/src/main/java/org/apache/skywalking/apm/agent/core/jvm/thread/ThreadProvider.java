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

package org.apache.skywalking.apm.agent.core.jvm.thread;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import org.apache.skywalking.apm.network.language.agent.v3.Thread;

public enum ThreadProvider {
    INSTANCE;
    private final ThreadMXBean threadMXBean;

    ThreadProvider() {
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    public Thread getThreadMetrics() {
        int newThreadCount = 0;
        int runnableThreadCount = 0;
        int blockedThreadCount = 0;
        int waitThreadCount = 0;
        int timeWaitThreadCount = 0;
        int terminatedThreadCount = 0;

        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds());
        if (threadInfos != null) {
            for (ThreadInfo threadInfo : threadInfos) {
                if (threadInfo != null) {
                    switch (threadInfo.getThreadState()) {
                        case NEW:
                            newThreadCount++;
                            break;
                        case RUNNABLE:
                            runnableThreadCount++;
                            break;
                        case BLOCKED:
                            blockedThreadCount++;
                            break;
                        case WAITING:
                            waitThreadCount++;
                            break;
                        case TIMED_WAITING:
                            timeWaitThreadCount++;
                            break;
                        case TERMINATED:
                            terminatedThreadCount++;
                            break;
                        default:
                            break;
                    }
                } else {
                    terminatedThreadCount++;
                }
            }
        }

        int threadCount = threadMXBean.getThreadCount();
        int daemonThreadCount = threadMXBean.getDaemonThreadCount();
        int peakThreadCount = threadMXBean.getPeakThreadCount();
        int deadlocked = threadMXBean.findDeadlockedThreads() != null ? threadMXBean.findDeadlockedThreads().length : 0;
        int monitorDeadlocked = threadMXBean.findMonitorDeadlockedThreads() != null ?
                threadMXBean.findMonitorDeadlockedThreads().length : 0;
        return Thread.newBuilder().setLiveCount(threadCount)
                .setDaemonCount(daemonThreadCount)
                .setPeakCount(peakThreadCount)
                .setDeadlocked(deadlocked)
                .setMonitorDeadlocked(monitorDeadlocked)
                .setNewThreadCount(newThreadCount)
                .setRunnableThreadCount(runnableThreadCount)
                .setBlockedThreadCount(blockedThreadCount)
                .setWaitThreadCount(waitThreadCount)
                .setTimeWaitThreadCount(timeWaitThreadCount)
                .setTerminatedThreadCount(terminatedThreadCount)
                .build();
    }

}
