package com.a.eye.skywalking.routing.router;

/**
 * Created by xin on 2016/11/29.
 */
public class RoutingService {
    public static Router router;

    public static Router getRouter() {
        if (router == null) {
            router = new Router();
        }
        return router;
    }

    public static void stop() {
        if (router != null)
            router.stop();
    }
}
