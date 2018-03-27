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

package org.apache.skywalking.apm.collector.agent.jetty.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.FileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public enum JsonFileReader {
    INSTANCE;

    private final Logger logger = LoggerFactory.getLogger(JsonFileReader.class);

    public JsonElement read(String fileName) throws FileNotFoundException {
        String path = this.getClass().getClassLoader().getResource(fileName).getFile();
        logger.debug("path: {}", path);
        JsonParser jsonParser = new JsonParser();
        return jsonParser.parse(new FileReader(path));
    }
}
