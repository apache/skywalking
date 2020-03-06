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
 */

package org.apache.skywalking.oap.server.receiver.trace.provider;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.receiver.trace.module.TraceModule;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class NoUpstreamRealAddressAgentConfig extends ConfigChangeWatcher {

    private final AtomicReference<String> settingsString;
    private volatile Languages ignoreByLanguage = Languages.EMPTY;

    public NoUpstreamRealAddressAgentConfig(TraceModuleProvider provider) {
        super(TraceModule.NAME, provider, "noUpstreamRealAddressAgent");
        this.settingsString = new AtomicReference<>(Const.EMPTY_STRING);
        final Languages defaultLanguages = parseLanguageFromFile("no_upstream_real_address_agent.yml");
        log.info("Default configured no upstream real address agent: {}", defaultLanguages);
        onLanguageUpdated(defaultLanguages);
    }

    private void activeSetting(String config) {
        if (log.isDebugEnabled()) {
            log.debug("Updating using new static config: {}", config);
        }
        this.settingsString.set(config);
        onLanguageUpdated(parseGatewayFromYml(config));
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            activeSetting("");
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return settingsString.get();
    }

    private void onLanguageUpdated(final Languages languages) {
        log.info("Updating no upstream real address agent with:{}", languages);
        languages.languages = languages.getLanguages().stream().map(String::toLowerCase).collect(Collectors.toSet());
        ignoreByLanguage = languages;
    }

    public boolean ignoreLanguage(final String language) {
        if (StringUtil.isEmpty(language)) {
            return false;
        }
        final boolean isIgnored = ignoreByLanguage.getLanguages().contains(language);
        if (log.isDebugEnabled() && isIgnored) {
            log.debug("Language [{}] is ignored", language);
        }
        return isIgnored;
    }

    private Languages parseLanguageFromFile(final String file) {
        try {
            final Reader reader = ResourceUtils.read(file);
            return new Yaml().loadAs(reader, Languages.class);
        } catch (FileNotFoundException e) {
            log.error("Cannot load languages from: {}", file, e);
        }
        return Languages.EMPTY;
    }

    private Languages parseGatewayFromYml(final String ymlContent) {
        try {
            return new Yaml().loadAs(ymlContent, Languages.class);
        } catch (Exception e) {
            log.error("Failed to parse yml content as languages: \n{}", ymlContent, e);
        }
        return Languages.EMPTY;
    }

    @ToString
    public static class Languages {

        static final Languages EMPTY = new Languages();

        @Setter
        @Getter
        private Set<String> languages = Collections.emptySet();
    }
}
