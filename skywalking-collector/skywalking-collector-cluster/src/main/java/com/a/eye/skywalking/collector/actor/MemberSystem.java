package com.a.eye.skywalking.collector.actor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MemberSystem {

    private Map<String, AbstractMember> memberMap = new HashMap();

    public AbstractMember memberOf(Class clazz, String role) {
        try {
            AbstractMember member = (AbstractMember) clazz.newInstance();
            memberMap.put(role, member);
            return member;
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public AbstractMember memberFor(String role) {
        return memberMap.get(role);
    }
}
