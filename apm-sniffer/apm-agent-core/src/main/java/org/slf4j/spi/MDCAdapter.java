package org.slf4j.spi;

import java.util.Map;

public interface MDCAdapter
{
    void put(String paramString1, String paramString2);

    String get(String paramString);

    void remove(String paramString);

    void clear();

    Map getCopyOfContextMap();

    void setContextMap(Map paramMap);
}