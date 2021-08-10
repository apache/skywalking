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

package org.apache.skywalking.oap.server.core.logging.log4j;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.plugins.Plugin;

/**
 * OapConfigurationFactory guarantee the log4j configuration object is {@link OapConfiguration}.
 * It guarantees the downcast {@link Configuration} to {@link OapConfiguration} safely.
 */
@Plugin(name = "OapConfigurationFactory", category = "ConfigurationFactory")
@Order(10)
public class OapConfigurationFactory extends ConfigurationFactory {
    /**
     * Valid file extensions for XML files.
     */
    public static final String[] SUFFIXES = new String[] {".xml", "*"};

    /**
     * Returns the file suffixes for XML files.
     * @return An array of File extensions.
     */
    @Override
    public String[] getSupportedTypes() {
        return SUFFIXES;
    }

    /**
     * Return the configuration.
     * @param loggerContext The logger context
     * @param source The input source
     * @return the configuration
     */
    @Override
    public Configuration getConfiguration(final LoggerContext loggerContext, final ConfigurationSource source) {
        return new OapConfiguration(loggerContext, source);
    }
}
