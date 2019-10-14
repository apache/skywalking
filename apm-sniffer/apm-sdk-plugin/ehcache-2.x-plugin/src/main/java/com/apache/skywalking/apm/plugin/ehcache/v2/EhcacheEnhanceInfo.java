package com.apache.skywalking.apm.plugin.ehcache.v2;

/**
 * @Author MrPro
 */
public class EhcacheEnhanceInfo {

    private String cacheName;

    public EhcacheEnhanceInfo() {
    }

    public EhcacheEnhanceInfo(String cacheName) {
        this.cacheName = cacheName;
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }
}
