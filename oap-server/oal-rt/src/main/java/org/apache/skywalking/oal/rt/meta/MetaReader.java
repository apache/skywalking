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

package org.apache.skywalking.oal.rt.meta;

import java.io.InputStream;
import java.io.Reader;
import org.yaml.snakeyaml.Yaml;

/**
 * @author wusheng
 */
public class MetaReader {
    public MetaSettings read(Reader reader) {
        Yaml yaml = new Yaml();
        MetaSettings settings = yaml.loadAs(reader, MetaSettings.class);

        return settings;
    }

    public MetaSettings read(InputStream reader) {
        Yaml yaml = new Yaml();
        MetaSettings settings = yaml.loadAs(reader, MetaSettings.class);

        return settings;
    }
}
