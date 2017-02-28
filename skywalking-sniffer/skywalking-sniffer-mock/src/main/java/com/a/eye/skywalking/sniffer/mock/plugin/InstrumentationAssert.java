package com.a.eye.skywalking.sniffer.mock.plugin;

import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassEnhancePluginDefine;

public class InstrumentationAssert {

    private ClassEnhancePluginDefine instrumentationClass;
    private String enhanceClass;

    public InstrumentationAssert(Class instrumentationClass) {
        try {
            this.instrumentationClass = (ClassEnhancePluginDefine) instrumentationClass.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public InstrumentationAssert enhanceClass(String enhanceClass) {
        this.enhanceClass = enhanceClass;
        return this;
    }


    public InstrumentationAssert constructor() {

        return this;
    }

    public void withParamType(Class... paramType) {
    }
}
