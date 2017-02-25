package com.a.eye.skywalking.plugin.dubbox;

/**
 * {@link BugFixActive#active} is an flag that present the dubbox version is below 2.8.3,
 * The version 2.8.3 of dubbox don't support attachment. so skywalking provided another way
 * to support the function that transport the serialized context data. The way is that
 * all parameters of dubbo service need to extend {@link SWBaseBean}, {@link com.a.eye.skywalking.plugin.dubbo.DubboInterceptor}
 * fetch the serialized context data by using {@link SWBaseBean#getTraceContext()}.
 *
 * @author zhangxin
 */
public final class BugFixActive {

    private static boolean active = false;

    /**
     * Set active status, before startup dubbo services.
     */
    public static void active() {
        BugFixActive.active = true;
    }


    public static boolean isActive() {
        return BugFixActive.active;
    }

}
