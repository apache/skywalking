package org.apache.skywalking.apm.plugin.seata.enhanced;

public interface EnhancedRequest {
  void put(final String key, final String value);

  String get(final String key);
}
