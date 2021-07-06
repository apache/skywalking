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
        int runnableStateThreadCount = 0;
        int blockedStateThreadCount = 0;
        int waitingStateThreadCount = 0;
        int timedWaitingStateThreadCount = 0;

        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 0);
        if (threadInfos != null) {
            for (ThreadInfo threadInfo : threadInfos) {
                if (threadInfo == null) {
                    continue;
                }
                switch (threadInfo.getThreadState()) {
                    case RUNNABLE:
                        runnableStateThreadCount++;
                        break;
                    case BLOCKED:
                        blockedStateThreadCount++;
                        break;
                    case WAITING:
                        waitingStateThreadCount++;
                        break;
                    case TIMED_WAITING:
                        timedWaitingStateThreadCount++;
                        break;
                    default:
                        break;
                    }
            }
        }

        int threadCount = threadMXBean.getThreadCount();
        int daemonThreadCount = threadMXBean.getDaemonThreadCount();
        int peakThreadCount = threadMXBean.getPeakThreadCount();
        return Thread.newBuilder().setLiveCount(threadCount)
                .setDaemonCount(daemonThreadCount)
                .setPeakCount(peakThreadCount)
                .setRunnableStateThreadCount(runnableStateThreadCount)
                .setBlockedStateThreadCount(blockedStateThreadCount)
                .setWaitingStateThreadCount(waitingStateThreadCount)
                .setTimedWaitingStateThreadCount(timedWaitingStateThreadCount)
                .build();
    }

}
