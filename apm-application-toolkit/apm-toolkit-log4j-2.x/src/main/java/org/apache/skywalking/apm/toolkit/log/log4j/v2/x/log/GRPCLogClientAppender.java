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

package org.apache.skywalking.apm.toolkit.log.log4j.v2.x.log;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

@Plugin(name = "GRPCLogClientAppender", category = "Core", elementType = "appender")
public class GRPCLogClientAppender extends AbstractAppender {

    private GRPCLogClientAppender(final String name, final Filter filter, final boolean ignoreExceptions) {
        super(name, filter, null, ignoreExceptions);
    }

    @Override
    public void append(LogEvent logEvent) {

    }

    @PluginFactory
    public static GRPCLogClientAppender createAppender(@PluginAttribute("name") final String name,
                                                       @PluginElement("Filter") final Filter filter,
                                                       @PluginConfiguration final Configuration config,
                                                       @PluginAttribute("ignoreExceptions") final String ignore) {

        String appenderName = name == null ? "gRPCLogClientAppender" : name;
        final boolean ignoreExceptions = "true".equalsIgnoreCase(ignore) || !"false".equalsIgnoreCase(ignore);
        return new GRPCLogClientAppender(appenderName, filter, ignoreExceptions);
    }
}
