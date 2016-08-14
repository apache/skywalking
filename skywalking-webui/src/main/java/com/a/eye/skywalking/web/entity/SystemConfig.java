package com.a.eye.skywalking.web.entity;

/**
 * Created by xin on 16-3-29.
 */
public class SystemConfig {
    private String configId;
    private String confKey;
    private String confValue;
    private String valueType;
    private String valueDesc;

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getConfigId() {
        return configId;
    }

    public void setConfKey(String confKey) {
        this.confKey = confKey;
    }

    public String getConfKey() {
        return confKey;
    }

    public void setConfValue(String confValue) {
        this.confValue = confValue;
    }

    public String getConfValue() {
        return confValue;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueDesc(String valueDesc) {
        this.valueDesc = valueDesc;
    }

    public String getValueDesc() {
        return valueDesc;
    }
}
