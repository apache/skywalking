package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 2017/7/15.
 */
public interface LogEvent {
    List<KeyValuePair> events();

    class Impl implements LogEvent {
        private List<KeyValuePair> keyValuePairs;

        Impl() {
            keyValuePairs = new ArrayList<>();
        }

        void add(String key, String value) {
            keyValuePairs.add(new KeyValuePair.Impl(key, value));
        }

        @Override
        public List<KeyValuePair> events() {
            return keyValuePairs;
        }
    }
}
