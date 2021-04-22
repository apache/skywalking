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

package org.apache.skywalking.apm.toolkit.log.logback.v1.x.logstash;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.FieldNamesAware;
import net.logstash.logback.composite.JsonWritingUtils;
import net.logstash.logback.fieldnames.LogstashFieldNames;

import java.io.IOException;
import java.util.Map;

public class SkyWalkingContextJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> implements FieldNamesAware<LogstashFieldNames> {

    public static final String SKYWALKING_CONTEXT = "SW_CTX";

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        String skyWalkingContext = getSkyWalkingContext(event);
        JsonWritingUtils.writeStringField(generator, getFieldName(), skyWalkingContext);
    }

    @Override
    public void setFieldNames(LogstashFieldNames fieldNames) {
        setFieldName(SKYWALKING_CONTEXT);
    }

    public String getSkyWalkingContext(ILoggingEvent event) {
        Map<String, String> map = event.getLoggerContextVO().getPropertyMap();
        return map.get(SKYWALKING_CONTEXT);
    }
}
