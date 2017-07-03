package org.skywalking.apm.network.trace.component;

/**
 * @author wusheng
 */
public class ComponentsDefine {
    public static final OfficialComponent TOMCAT = new OfficialComponent(1, "Tomcat");

    public static final OfficialComponent HTTPCLIENT = new OfficialComponent(2, "HttpClient");

    public static final OfficialComponent DUBBO = new OfficialComponent(3, "Dubbo");
}
