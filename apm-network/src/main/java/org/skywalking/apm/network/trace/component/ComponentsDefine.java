package org.skywalking.apm.network.trace.component;

/**
 * The supported list of skywalking java sniffer.
 *
 * @author wusheng
 */
public class ComponentsDefine {
    public static final OfficialComponent TOMCAT = new OfficialComponent(1, "Tomcat");

    public static final OfficialComponent HTTPCLIENT = new OfficialComponent(2, "HttpClient");

    public static final OfficialComponent DUBBO = new OfficialComponent(3, "Dubbo");

    public static final OfficialComponent H2 = new OfficialComponent(4, "H2");

    public static final OfficialComponent MYSQL = new OfficialComponent(5, "Mysql");

    public static final OfficialComponent ORACLE = new OfficialComponent(6, "ORACLE");

    public static final OfficialComponent REDIS = new OfficialComponent(7, "Redis");

    public static final OfficialComponent MOTAN = new OfficialComponent(8, "Motan");

    public static final OfficialComponent MONGODB = new OfficialComponent(9, "MongoDB");

    public static final OfficialComponent RESIN = new OfficialComponent(10, "Resin");

    public static final OfficialComponent FEIGN = new OfficialComponent(11, "Feign");

    public static final OfficialComponent OKHTTP = new OfficialComponent(12, "OKHttp");
}
