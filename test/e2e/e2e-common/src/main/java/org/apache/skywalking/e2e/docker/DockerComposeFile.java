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

package org.apache.skywalking.e2e.docker;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.Data;

import static java.util.stream.Collectors.joining;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Data
public final class DockerComposeFile {
    private String version;
    private Map<String, Map<String, Object>> services;
    private Map<String, Map<String, Object>> networks;

    public static DockerComposeFile getAllConfigInfo(List<String> composeFiles) throws IOException, InterruptedException {
        String shStr = String.format("docker-compose %s config",
                composeFiles.stream().collect(joining(" -f", " -f", "")));
        Process process = Runtime.getRuntime().exec(shStr, null, null);
        InputStreamReader ir = new InputStreamReader(process.getInputStream());
        LineNumberReader input = new LineNumberReader(ir);
        String line;
        StringBuilder result = new StringBuilder();
        process.waitFor();
        while ((line = input.readLine()) != null) {
            result.append(line).append("\n");
        }
        return load(result).as(DockerComposeFile.class);
    }

    public List<String> getServiceExposedPorts(String serviceName) {
        Map<String, Object> service = services.get(serviceName);
        List tmp = (List) service.get("expose");
        if (tmp == null) {
            return new LinkedList<>();
        }
        List<String> ports = new LinkedList<>();
        for (Object item: tmp) {
            ports.add(item.toString());
        }
        return ports;
    }

    public boolean isExposedPort(String serviceName, Integer port) {
        List<String> ports = getServiceExposedPorts(serviceName);
        return ports.contains(String.valueOf(port));
    }

}
