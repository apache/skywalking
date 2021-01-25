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
import java.util.Arrays;
import java.util.HashMap;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.junit.Assert;
import org.junit.Test;

public class AnalyzerSettingTest {

    private final Gson gson = new Gson();

    private static final String ANALYZER_JSON = "{\"analyzer\":{\"my_custom_analyzer\":{\"type\":\"custom\",\"char_filter\":[\"emoticons\"],\"tokenizer\":\"punctuation\",\"filter\":[\"lowercase\",\"english_stop\"]}},\"tokenizer\":{\"punctuation\":{\"type\":\"pattern\",\"pattern\":\"[ .,!?]\"}},\"char_filter\":{\"emoticons\":{\"type\":\"mapping\",\"mappings\":[\":) => _happy_\",\":( => _sad_\"]}},\"filter\":{\"english_stop\":{\"type\":\"stop\",\"stopwords\":\"_english_\"}}}";

    @Test
    public void combine() {
        StorageModuleElasticsearchConfig elasticsearchConfig = new StorageModuleElasticsearchConfig();
        AnalyzerSetting oapAnalyzerSetting = gson.fromJson(elasticsearchConfig.getOapAnalyzer(), AnalyzerSetting.class);
        Assert.assertEquals(oapAnalyzerSetting, getDefaultOapAnalyzer());
        AnalyzerSetting oapLogAnalyzerSetting = gson.fromJson(
            elasticsearchConfig.getOapLogAnalyzer(), AnalyzerSetting.class);
        Assert.assertEquals(oapLogAnalyzerSetting, getDefaultOapLogAnalyzer());
        AnalyzerSetting testAnalyzerSetting = gson.fromJson(ANALYZER_JSON, AnalyzerSetting.class);
        Assert.assertEquals(testAnalyzerSetting, getTestOapAnalyzerSetting());
        oapAnalyzerSetting.combine(oapLogAnalyzerSetting);
        oapAnalyzerSetting.combine(testAnalyzerSetting);
        Assert.assertEquals(oapAnalyzerSetting, getMergedAnalyzerSetting());
    }

    private AnalyzerSetting getMergedAnalyzerSetting() {
        AnalyzerSetting analyzerSetting = new AnalyzerSetting();
        analyzerSetting.setTokenizer(new HashMap<String, Object>() {
            {
                put("punctuation", new HashMap<String, Object>() {
                    {
                        put("type", "pattern");
                        put("pattern", "[ .,!?]");
                    }
                });
            }
        });
        analyzerSetting.setCharFilter(new HashMap<String, Object>() {
            {
                put("emoticons", new HashMap<String, Object>() {
                    {
                        put("type", "mapping");
                        put("mappings", Arrays.asList(":) => _happy_", ":( => _sad_"));
                    }
                });
            }
        });
        analyzerSetting.setFilter(new HashMap<String, Object>() {
            {
                put("english_stop", new HashMap<String, Object>() {
                    {
                        put("type", "stop");
                        put("stopwords", "_english_");
                    }
                });
            }
        });
        analyzerSetting.setAnalyzer(new HashMap<String, Object>() {
            {
                put("my_custom_analyzer", new HashMap<String, Object>() {
                    {
                        put("type", "custom");
                        put("char_filter", Arrays.asList("emoticons"));
                        put("tokenizer", "punctuation");
                        put("filter", Arrays.asList("lowercase", "english_stop"));
                    }
                });
                put("oap_log_analyzer", new HashMap<String, Object>() {
                    {
                        put("type", "standard");
                    }
                });
                put("oap_analyzer", new HashMap<String, Object>() {
                    {
                        put("type", "stop");
                    }
                });
            }
        });
        return analyzerSetting;
    }

    private AnalyzerSetting getTestOapAnalyzerSetting() {
        AnalyzerSetting analyzerSetting = new AnalyzerSetting();
        analyzerSetting.setTokenizer(new HashMap<String, Object>() {
            {
                put("punctuation", new HashMap<String, Object>() {
                    {
                        put("type", "pattern");
                        put("pattern", "[ .,!?]");
                    }
                });
            }
        });
        analyzerSetting.setCharFilter(new HashMap<String, Object>() {
            {
                put("emoticons", new HashMap<String, Object>() {
                    {
                        put("type", "mapping");
                        put("mappings", Arrays.asList(":) => _happy_", ":( => _sad_"));
                    }
                });
            }
        });
        analyzerSetting.setFilter(new HashMap<String, Object>() {
            {
                put("english_stop", new HashMap<String, Object>() {
                    {
                        put("type", "stop");
                        put("stopwords", "_english_");
                    }
                });
            }
        });
        analyzerSetting.setAnalyzer(new HashMap<String, Object>() {
            {
                put("my_custom_analyzer", new HashMap<String, Object>() {
                    {
                        put("type", "custom");
                        put("char_filter", Arrays.asList("emoticons"));
                        put("tokenizer", "punctuation");
                        put("filter", Arrays.asList("lowercase", "english_stop"));
                    }
                });
            }
        });
        return analyzerSetting;
    }

    private AnalyzerSetting getDefaultOapAnalyzer() {
        AnalyzerSetting analyzerSetting = new AnalyzerSetting();
        HashMap<String, Object> map = new HashMap<>();
        map.put("oap_analyzer", new HashMap<String, Object>() {
            {
                put("type", "stop");
            }
        });
        analyzerSetting.setAnalyzer(map);
        return analyzerSetting;
    }

    private AnalyzerSetting getDefaultOapLogAnalyzer() {
        AnalyzerSetting analyzerSetting = new AnalyzerSetting();
        HashMap<String, Object> analyzerMap = new HashMap<String, Object>() {
            {
                put("oap_log_analyzer", new HashMap<String, Object>() {
                    {
                        put("type", "standard");
                    }
                });
            }
        };
        analyzerSetting.setAnalyzer(analyzerMap);
        return analyzerSetting;
    }
}