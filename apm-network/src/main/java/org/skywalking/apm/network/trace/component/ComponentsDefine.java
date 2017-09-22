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

    public static final OfficialComponent SPRING_REST_TEMPLATE = new OfficialComponent(13, "SpringRestTemplate");

    public static final OfficialComponent SPRING_MVC_ANNOTATION = new OfficialComponent(14, "SpringMVC");

    public static final OfficialComponent STRUTS2 = new OfficialComponent(15, "Struts2");

    public static final OfficialComponent NUTZ_MVC_ANNOTATION = new OfficialComponent(16, "NutzMVC");

    public static final OfficialComponent NUTZ_HTTP = new OfficialComponent(17, "NutzHttp");

    private static ComponentsDefine instance = new ComponentsDefine();

    private String[] components;

    public static ComponentsDefine getInstance() {
        return instance;
    }

    public ComponentsDefine() {
        components = new String[18];
        addComponent(TOMCAT);
        addComponent(HTTPCLIENT);
        addComponent(DUBBO);
        addComponent(H2);
        addComponent(MYSQL);
        addComponent(ORACLE);
        addComponent(REDIS);
        addComponent(MOTAN);
        addComponent(MONGODB);
        addComponent(RESIN);
        addComponent(FEIGN);
        addComponent(OKHTTP);
        addComponent(SPRING_REST_TEMPLATE);
        addComponent(SPRING_MVC_ANNOTATION);
        addComponent(STRUTS2);
        addComponent(NUTZ_MVC_ANNOTATION);
        addComponent(NUTZ_HTTP);
    }

    private void addComponent(OfficialComponent component) {
        components[component.getId()] = component.getName();
    }

    public String getComponentName(int componentId) {
        if (componentId > components.length - 1 || componentId == 0) {
            return null;
        } else {
            return components[componentId];
        }
    }
}
