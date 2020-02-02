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
package org.apache.skywalking.oap.server.core.profile;

import org.apache.skywalking.oap.server.core.profile.analyze.ProfileAnalyzer;
import org.apache.skywalking.oap.server.core.query.entity.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.entity.ProfileStackElement;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * @author MrPro
 */
public class ProfileAnalyzeGenerator {

    public void generateStack() {
        Thread watchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    methodA();
                } catch (InterruptedException e) {
                }
            }
        });

        watchThread.start();
        printWatchThread(watchThread);
    }

    public void generateAnalyzation() {
        InputStream expectedInputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("thread-snapshot.yml");
        ProfileStackHolder holder = new Yaml().loadAs(expectedInputStream, ProfileStackHolder.class);

        ProfileAnalyzation analyze = ProfileAnalyzer.analyze(holder.getList());
        printAnalyzation(analyze);
    }

    private void printAnalyzation(ProfileAnalyzation analyzation) {
        System.out.println("stack:");
        for (ProfileStackElement element : analyzation.getStack()) {
            System.out.println(printStackElement(element, 0));
        }
    }

    private String printStackElement(ProfileStackElement element, int deep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= deep; i++) {
            sb.append("\t");
        }
        String prefix = sb.toString();

        sb = new StringBuilder();
        sb.append(prefix).append("- codeSignature: ").append(element.getCodeSignature()).append("\n");
        sb.append(prefix).append("  duration: ").append(element.getDuration()).append("\n");
        sb.append(prefix).append("  durationChildExcluded: ").append(element.getDurationChildExcluded()).append("\n");
        sb.append(prefix).append("  count: ").append(element.getCount()).append("\n");
        if (!CollectionUtils.isEmpty(element.getChilds())) {
            sb.append(prefix).append("  childs: \n");
            for (ProfileStackElement child : element.getChilds()) {
                sb.append(printStackElement(child, deep + 1));
            }
        }

        return sb.toString();
    }

    private void printWatchThread(Thread thread) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int seq = 0;
                long currentLoopStartTime = -1;
                System.out.println("list:");
                while (thread.isAlive()) {
                    currentLoopStartTime = System.currentTimeMillis();
                    StackTraceElement[] stackElements = thread.getStackTrace();
                    if (stackElements.length == 0) {
                        break;
                    }

                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("- dumpTime: ").append(System.currentTimeMillis()).append("\n");
                    stringBuilder.append("  sequence: ").append(seq++).append("\n");
                    stringBuilder.append("  stack:").append("\n");
                    for (int i = stackElements.length - 1; i >= 0; i--) {
                        stringBuilder.append("\t").append("  - ").append(stackElements[i].getClassName()).append(".").append(stackElements[i].getMethodName()).append(":").append(stackElements[i].getLineNumber()).append("\n");
                    }

                    long needToSleep = (currentLoopStartTime + 10) - System.currentTimeMillis();
                    needToSleep = needToSleep > 0 ? needToSleep : 10;
                    try {
                        Thread.sleep(needToSleep);
                    } catch (InterruptedException e) {
                    }
                    System.out.print(stringBuilder.toString());
                }

            }
        }).start();
    }

    private static void methodA() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(30);
        for (int i = 0; i < 2; i++) {
            methodB();
        }
    }

    private static void methodB() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(20);
    }

    public static void main(String[] args) {
        new ProfileAnalyzeGenerator().generateStack();
    }

}
