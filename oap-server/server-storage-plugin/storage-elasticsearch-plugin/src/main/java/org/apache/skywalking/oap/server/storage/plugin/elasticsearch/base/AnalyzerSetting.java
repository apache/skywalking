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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;

@Getter
@Setter
@Slf4j
public class AnalyzerSetting {
    /**
     * A built-in or customised tokenizer.
     */
    private Map<String, Object> tokenizer = new HashMap<>();
    /**
     * An optional array of built-in or customised character filters.
     */
    @SerializedName("char_filter")
    private Map<String, Object> charFilter = new HashMap<>();
    /**
     * An optional array of built-in or customised token filters.
     */
    private Map<String, Object> filter = new HashMap<>();
    /**
     * The custom analyzers.
     */
    private Map<String, Object> analyzer = new HashMap<>();

    public void combine(AnalyzerSetting analyzerSetting) {
        this.analyzer.putAll(analyzerSetting.getAnalyzer());
        this.tokenizer.putAll(analyzerSetting.tokenizer);
        this.filter.putAll(analyzerSetting.filter);
        this.charFilter.putAll(analyzerSetting.charFilter);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AnalyzerSetting))
            return false;
        final AnalyzerSetting that = (AnalyzerSetting) o;
        return getTokenizer().equals(that.getTokenizer()) &&
            getCharFilter().equals(that.getCharFilter()) &&
            getFilter().equals(that.getFilter()) &&
            getAnalyzer().equals(that.getAnalyzer());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTokenizer(), getCharFilter(), getFilter(), getAnalyzer());
    }

    public enum Generator {
        OAP_ANALYZER_SETTING_GENERATOR(
            Column.AnalyzerType.OAP_ANALYZER,
            config -> new Gson().fromJson(config.getOapAnalyzer(), AnalyzerSetting.class)
        ),
        OAP_LOG_ANALYZER_SETTING_GENERATOR(
            Column.AnalyzerType.OAP_LOG_ANALYZER,
            config -> new Gson().fromJson(config.getOapLogAnalyzer(), AnalyzerSetting.class)
        );

        private final Column.AnalyzerType type;
        private final GenerateAnalyzerSettingFunc func;

        Generator(final Column.AnalyzerType type,
                  final GenerateAnalyzerSettingFunc func) {
            this.type = type;
            this.func = func;
        }

        public GenerateAnalyzerSettingFunc getGenerateFunc() {
            return this.func;
        }

        public static Generator getGenerator(Column.AnalyzerType type) throws StorageException {
            for (final Generator value : Generator.values()) {
                if (value.type == type) {
                    return value;
                }
            }
            throw new StorageException("cannot found the AnalyzerSettingGenerator for the " + type.getName() + " type");
        }
    }

    @FunctionalInterface
    public interface GenerateAnalyzerSettingFunc {
        AnalyzerSetting generate(StorageModuleElasticsearchConfig config);
    }
}


