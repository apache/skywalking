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

package org.apache.skywalking.apm.agent.core.context.util;

import static org.apache.skywalking.apm.agent.core.conf.Config.Agent.CAUSE_EXCEPTION_DEPTH;

/**
 * {@link ThrowableTransformer} is responsible for transferring stack trace of throwable.
 */
public enum ThrowableTransformer {
    INSTANCE;

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    public String convert2String(Throwable throwable, final int maxLength) {
        final StringBuilder stackMessage = new StringBuilder();
        Throwable causeException = throwable;

        int depth = CAUSE_EXCEPTION_DEPTH;
        while (causeException != null && depth != 0) {
            stackMessage.append(printExceptionInfo(causeException));

            boolean isLookDeeper = printStackElement(causeException.getStackTrace(), new AppendListener() {
                public void append(String value) {
                    stackMessage.append(value);
                }

                public boolean overMaxLength() {
                    return stackMessage.length() > maxLength;
                }
            });

            if (isLookDeeper) {
                break;
            }

            causeException = causeException.getCause();
            depth--;
        }

        return stackMessage.toString();
    }

    private String printExceptionInfo(Throwable causeException) {
        return causeException.toString() + LINE_SEPARATOR;
    }

    private boolean printStackElement(StackTraceElement[] stackTrace, AppendListener printListener) {
        if (stackTrace.length == 0) {
            /**
             * In some cases, people would fill empty stackTrace intentionally.
             * This is a quick stop.
             */
            return true;
        }

        for (StackTraceElement traceElement : stackTrace) {
            printListener.append("at " + traceElement + LINE_SEPARATOR);
            if (printListener.overMaxLength()) {
                return true;
            }
        }
        return false;
    }

    private interface AppendListener {
        void append(String value);

        boolean overMaxLength();
    }
}
