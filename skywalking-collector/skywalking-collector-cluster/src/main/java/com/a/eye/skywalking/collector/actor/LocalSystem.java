package com.a.eye.skywalking.collector.actor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class LocalSystem {

    private static Map<String, Worker> context = new HashMap();

    public static void actorOf(Class clazz, String role) {
        try {
            Worker classInstance = (Worker) clazz.newInstance();
            context.put(clazz.getName() + "_" + role, classInstance);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static Worker actorFor(Class clazz, String role) {
        return context.get(clazz.getName() + "_" + role);
    }
}
