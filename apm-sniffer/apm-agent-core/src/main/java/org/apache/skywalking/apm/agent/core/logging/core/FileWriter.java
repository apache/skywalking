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


package org.apache.skywalking.apm.agent.core.logging.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.Constants;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

/**
 * The <code>FileWriter</code> support async file output, by using a queue as buffer.
 *
 * @author wusheng
 */
public class FileWriter implements IWriter {
    private static FileWriter INSTANCE;
    private static final Object CREATE_LOCK = new Object();
    private FileOutputStream fileOutputStream;
    private ArrayBlockingQueue logBuffer;
    private volatile int fileSize;

    public static FileWriter get() {
        if (INSTANCE == null) {
            synchronized (CREATE_LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new FileWriter();
                }
            }
        }
        return INSTANCE;
    }

    private FileWriter() {
        logBuffer = new ArrayBlockingQueue(1024);
        final ArrayList<String> outputLogs = new ArrayList<String>(200);
        Executors
            .newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("LogFileWriter"))
            .scheduleAtFixedRate(new RunnableWithExceptionProtection(new Runnable() {
                @Override public void run() {
                    logBuffer.drainTo(outputLogs);
                    for (String log : outputLogs) {
                        writeToFile(log + Constants.LINE_SEPARATOR);
                    }
                    try {
                        fileOutputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, new RunnableWithExceptionProtection.CallbackWhenException() {
                @Override public void handle(Throwable t) {
                }
            }
            ), 0, 1, TimeUnit.SECONDS);
    }

    /**
     * @param message to be written into the file.
     */
    private void writeToFile(String message) {
        if (prepareWriteStream()) {
            try {
                fileOutputStream.write(message.getBytes());
                fileSize += message.length();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                switchFile();
            }
        }
    }

    private void switchFile() {
        if (fileSize > Config.Logging.MAX_FILE_SIZE) {
            forceExecute(new Callable() {
                @Override
                public Object call() throws Exception {
                    fileOutputStream.flush();
                    return null;
                }
            });
            forceExecute(new Callable() {
                @Override
                public Object call() throws Exception {
                    fileOutputStream.close();
                    return null;
                }
            });
            forceExecute(new Callable() {
                @Override
                public Object call() throws Exception {
                    new File(Config.Logging.DIR, Config.Logging.FILE_NAME)
                        .renameTo(new File(Config.Logging.DIR,
                            Config.Logging.FILE_NAME + new SimpleDateFormat(".yyyy_MM_dd_HH_mm_ss").format(new Date())));
                    return null;
                }
            });
            forceExecute(new Callable() {
                @Override
                public Object call() throws Exception {
                    fileOutputStream = null;
                    return null;
                }
            });
        }
    }

    private void forceExecute(Callable callable) {
        try {
            callable.call();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @return true if stream is prepared ready.
     */
    private boolean prepareWriteStream() {
        if (fileOutputStream != null) {
            return true;
        }
        File logFilePath = new File(Config.Logging.DIR);
        if (!logFilePath.exists()) {
            logFilePath.mkdirs();
        } else if (!logFilePath.isDirectory()) {
            System.err.println("Log dir(" + Config.Logging.DIR + ") is not a directory.");
        }
        try {
            fileOutputStream = new FileOutputStream(new File(logFilePath, Config.Logging.FILE_NAME), true);
            fileSize = Long.valueOf(new File(logFilePath, Config.Logging.FILE_NAME).length()).intValue();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return fileOutputStream != null;
    }

    /**
     * Write log to the queue.
     * W/ performance trade off, set 2ms timeout for the log OP.
     *
     * @param message to log
     */
    @Override public void write(String message) {
        try {
            logBuffer.offer(message, 2, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
