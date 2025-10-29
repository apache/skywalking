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

package org.apache.skywalking.oap.server.library.pprof.parser;

import com.google.perftools.profiles.ProfileProto;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import org.apache.skywalking.oap.server.library.pprof.type.FrameTree;
import org.apache.skywalking.oap.server.library.pprof.type.FrameTreeBuilder;

/**
 * Parses pprof protobuf format files and converts them to frame trees.
 */
public class PprofParser {

    public static FrameTree dumpTree(ByteBuffer buf) throws IOException {
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        InputStream stream = new java.io.ByteArrayInputStream(bytes);
        InputStream inputStream = new GZIPInputStream(stream);
        ProfileProto.Profile profile = ProfileProto.Profile.parseFrom(inputStream);
        FrameTree tree = new FrameTreeBuilder(profile).build();
        return tree;
    }

    public static FrameTree dumpTree(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("Pprof file not found: " + filePath);
        }
        InputStream fileStream = new FileInputStream(file);
        InputStream stream = new GZIPInputStream(fileStream);
        ProfileProto.Profile profile = ProfileProto.Profile.parseFrom(stream);
        FrameTree tree = new FrameTreeBuilder(profile).build();
        return tree;
    }

    /**
     * Resolve function signature for a given location id. The signature format matches FrameTreeBuilder
     * (functionName:line;... when inlined, joined by ';').
     */
    public static String resolveSignature(long locationId, ProfileProto.Profile profile) {
        if (locationId == 0) {
            return "root";
        }
        ProfileProto.Location location = profile.getLocation((int) locationId - 1);
        return location.getLineList().stream().map(line -> {
            ProfileProto.Function function = profile.getFunction((int) line.getFunctionId() - 1);
            String functionName = profile.getStringTable((int) function.getName());
            return functionName + ":" + line.getLine();
        }).collect(java.util.stream.Collectors.joining(";"));
    }
}
