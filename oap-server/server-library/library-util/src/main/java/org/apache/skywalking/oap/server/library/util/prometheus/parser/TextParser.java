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

package org.apache.skywalking.oap.server.library.util.prometheus.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.skywalking.oap.server.library.util.prometheus.Parser;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.MetricFamily;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.MetricType;
import org.apache.skywalking.oap.server.library.util.prometheus.parser.sample.TextSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextParser implements Parser {
    private static final Logger LOG = LoggerFactory.getLogger(TextParser.class);

    private final BufferedReader reader;

    private String lastLineReadFromStream;

    public TextParser(final InputStream inputStream) {
        this.reader = new BufferedReader(new InputStreamReader(inputStream));
    }

    @Override
    public MetricFamily parse(long now) throws IOException {
        String line;
        if (lastLineReadFromStream != null) {
            line = lastLineReadFromStream;
            lastLineReadFromStream = null;
        } else {
            line = reader.readLine();
        }
        if (line == null) {
            return null;
        }

        Context ctx = new Context(now);
        while (line != null) {
            line = line.trim();

            try {
                if (parseLine(line, ctx)) {
                    break;
                }
            } catch (Exception e) {
                LOG.debug("Failed to process line - it will be ignored: {}", line, e);
            }

            line = reader.readLine();
        }

        if (!ctx.name.isEmpty()) {
            ctx.end();
        }

        return ctx.metricFamily;
    }

    private boolean parseLine(String line, Context ctx) {
        if (line.isEmpty()) {
            return false;
        }
        if (line.charAt(0) == '#') {
            String[] parts = line.split("[ \t]+", 4);
            if (parts.length < 3) {
                return false;
            }
            if (parts[1].equals("HELP")) {
                if (!parts[2].equals(ctx.name)) {
                    if (!ctx.name.isEmpty()) {
                        this.lastLineReadFromStream = line;
                        return true;
                    }
                    ctx.clear();
                    ctx.name = parts[2];
                    ctx.type = MetricType.GAUGE;
                    ctx.allowedNames.add(parts[2]);
                }
                if (parts.length == 4) {
                    ctx.help = StringEscapeUtils.escapeJava(parts[3]);
                }
            } else if (parts[1].equals("TYPE")) {
                if (!parts[2].equals(ctx.name)) {
                    if (!ctx.name.isEmpty()) {
                        this.lastLineReadFromStream = line;
                        return true;
                    }
                    ctx.clear();
                    ctx.name = parts[2];
                }
                ctx.addAllowedNames(parts[3]);
            }
            return false;
        }
        TextSample sample = TextSample.parse(line);
        if (!ctx.allowedNames.contains(sample.getName())) {
            if (!ctx.name.isEmpty()) {
                this.lastLineReadFromStream = line;
                return true;
            }
            ctx.clear();
            LOG.debug("Ignoring an unexpected metric: {}", line);
        } else {
            ctx.samples.add(sample);
        }
        return false;
    }
}
