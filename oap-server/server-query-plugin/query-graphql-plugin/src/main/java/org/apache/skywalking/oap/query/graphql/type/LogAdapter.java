/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.query.graphql.type;

import static java.lang.String.format;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import com.google.common.base.Splitter;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LogAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogAdapter.class);

    private final InternalLog log;

    // k8s promises RFC3339 or RFC3339Nano timestamp, we truncate to RFC3339
    private final DateTimeFormatter rfc3339Formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZZZZZ")
            .withResolverStyle(ResolverStyle.LENIENT);

    public Log adapt() {
        Log l = new Log();

        List<String> timeAndContent = Splitter.on(" ")
            .limit(2)
            .trimResults()
            .splitToList(log.line());
        if (timeAndContent.size() == 2) {
            String timeStr = timeAndContent.get(0).replaceAll("\\.\\d+Z", "Z");
            try {
                TemporalAccessor t = rfc3339Formatter.parse(timeStr);
                long timestamp = Instant.from(t).getEpochSecond();
                l.setTimestamp(timestamp);
                l.setContent(format("[%s] %s", log.container(), timeAndContent.get(1)));
                l.setContentType(ContentType.TEXT);
            } catch (Exception e) {
                LOGGER.warn("Failed to parse log entry, {}", log, e);
            }
        }

        return l;
    }
}
