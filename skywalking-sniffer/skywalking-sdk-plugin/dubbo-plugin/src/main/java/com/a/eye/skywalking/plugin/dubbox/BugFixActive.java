package com.a.eye.skywalking.plugin.dubbox;

/**
 * {@link BugFixActive#active} is an flag that present the dubbox version is below 2.8.3,
 * The version 2.8.3 of dubbox don't support attachment. so skywalking provided another way
 * to support the function that transport the serialized context data. the way that
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


    public static boolean isActive(){
        return BugFixActive.active;
    }

}
