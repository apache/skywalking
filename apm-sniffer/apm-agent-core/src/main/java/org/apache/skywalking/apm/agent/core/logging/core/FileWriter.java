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

import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.Constants;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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
    private Pattern filenamePattern = Pattern.compile(Config.Logging.FILE_NAME + "\\.\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}");

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
                    try {
                        logBuffer.drainTo(outputLogs);
                        for (String log : outputLogs) {
                            writeToFile(log + Constants.LINE_SEPARATOR);
                        }
                        try {
                            fileOutputStream.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } finally {
                        outputLogs.clear();
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

            if (Config.Logging.MAX_HISTORY_FILES > 0) {
                deleteExpiredFiles();
            }
        }
    }

    /**
     * load history log file name array
     * @return history log file name array
     */
    private String[] getHistoryFilePath() {
        File path = new File(Config.Logging.DIR);
        String[] pathArr = path.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return filenamePattern.matcher(name).matches();
            }
        });

        return pathArr;
    }

    /**
     * delete expired log files
     */
    private void deleteExpiredFiles() {
        String[] historyFileArr = getHistoryFilePath();
        if (historyFileArr != null && historyFileArr.length > Config.Logging.MAX_HISTORY_FILES) {

            Arrays.sort(historyFileArr, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return o2.compareTo(o1);
                }
            });

            for (int i = Config.Logging.MAX_HISTORY_FILES; i < historyFileArr.length; i++) {
                File expiredFile = new File(Config.Logging.DIR, historyFileArr[i]);
                expiredFile.delete();
            }
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
     * Write log to the queue. W/ performance trade off, set 2ms timeout for the log OP.
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
