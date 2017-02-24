package com.a.eye.skywalking.plugin.dubbox;

/**
 * {@link BugFixActive#active} is an flag that present the dubbox version is below 2.8.3,
 * The version 2.8.3 of dubbox don't support attachment. so skywalking provided another way
 * to support the function that transport the serialized context data. The way is that
 * all parameters of dubbo service need to extend {@link SWBaseBean}, {@link com.a.eye.skywalking.plugin.dubbo.DubboInterceptor}
 * fetch the serialized context data by using {@link SWBaseBean#getContextData()}.
 *
 * @author zhangxin
 */
public final class BugFixActive {

    private static boolean active = false;

    /**
     * This method should be call first if the dubbo version is below 2.8.3.
     */
    public static void active() {
        BugFixActive.active = true;
    }


    public static boolean isActive() {
        return BugFixActive.active;
    }

}
