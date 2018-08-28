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

package org.apache.skywalking.oap.server.library.buffer;

import java.io.*;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.skywalking.apm.util.StringUtil;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class DataStream {

    private static final Logger logger = LoggerFactory.getLogger(DataStream.class);

    private final File directory;
    private final int dataFileMaxSize;

    private boolean initialized = false;
    @Getter private BufferOutputStream outputStream;
    @Getter private BufferInputStream inputStream;
    private OffsetStream offsetStream;
    private final OutputStreamCreator streamCreator;

    DataStream(File directory, int dataFileMaxSize, int offsetFileMaxSize) {
        this.directory = directory;
        this.dataFileMaxSize = dataFileMaxSize;
        this.offsetStream = new OffsetStream(directory, offsetFileMaxSize);
        this.streamCreator = new OutputStreamCreator();
    }

    void clean() throws IOException {
        String[] fileNames = directory.list(new PrefixFileFilter(BufferFileUtils.DATA_FILE_PREFIX));
        if (fileNames != null) {
            for (String fileName : fileNames) {
                File file = new File(directory, fileName);
                if (logger.isDebugEnabled()) {
                    logger.debug("Delete buffer data file: {}", file.getAbsolutePath());
                }
                FileUtils.forceDelete(file);
            }
        }
        offsetStream.clean();
    }

    synchronized void initialize() throws IOException {
        if (!initialized) {
            offsetStream.initialize();

            Offset offset = offsetStream.getOffset();
            String writeFileName = offset.getWriteOffset().getFileName();

            File dataFile;
            if (StringUtil.isEmpty(writeFileName)) {
                dataFile = createFirstFile();
            } else {
                dataFile = new File(directory, writeFileName);
                if (!dataFile.exists()) {
                    dataFile = createFirstFile();
                }
            }

            createOutputStream(dataFile);
            createInputStream(dataFile);
            initialized = true;
        }
    }

    private File createFirstFile() throws IOException {
        File dataFile = createNewOne();

        offsetStream.getOffset().getReadOffset().setFileName(dataFile.getName());
        offsetStream.getOffset().getReadOffset().setOffset(0);

        return dataFile;
    }

    private File createNewOne() throws IOException {
        File dataFile = createNewFile();

        offsetStream.getOffset().getWriteOffset().setFileName(dataFile.getName());
        offsetStream.getOffset().getWriteOffset().setOffset(0);

        return dataFile;
    }

    private File createNewFile() throws IOException {
        String fileName = BufferFileUtils.buildFileName(directory, BufferFileUtils.DATA_FILE_PREFIX);
        File dataFile = new File(directory, fileName);

        boolean created = dataFile.createNewFile();
        if (!created) {
            logger.info("The file named {} already exists.", dataFile.getAbsolutePath());
        } else {
            logger.info("Create a new buffer data file: {}", dataFile.getAbsolutePath());
        }

        return dataFile;
    }

    private void createOutputStream(File dataFile) throws IOException {
        outputStream = new BufferOutputStream(FileUtils.openOutputStream(dataFile, true), offsetStream, dataFileMaxSize, streamCreator);
    }

    private void createInputStream(File dataFile) throws IOException {
        inputStream = new BufferInputStream(FileUtils.openInputStream(dataFile), offsetStream);
    }

    class OutputStreamCreator implements BufferOutputStream.CallBack {

        @Override public FileOutputStream create() throws IOException {
            return FileUtils.openOutputStream(createNewOne(), true);
        }
    }
}
