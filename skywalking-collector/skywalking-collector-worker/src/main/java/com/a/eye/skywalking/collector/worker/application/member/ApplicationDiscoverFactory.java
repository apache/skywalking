package com.a.eye.skywalking.collector.worker.application.member;

import com.a.eye.skywalking.collector.actor.AbstractMemberProvider;

/**
 * @author pengys5
 */
public class ApplicationDiscoverFactory extends AbstractMemberProvider {

    @Override
    public Class memberClass() {
        return ApplicationDiscoverMember.class;
    }
}
