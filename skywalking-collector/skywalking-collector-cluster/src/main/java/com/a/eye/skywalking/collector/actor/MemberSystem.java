package com.a.eye.skywalking.collector.actor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MemberSystem {

    private Map<String, AbstractMember> memberMap = new HashMap();

    public void memberOf(AbstractMember member, String role) {
        memberMap.put(role, member);
    }

    public AbstractMember memberFor(String role) {
        return memberMap.get(role);
    }
}
