package org.apache.skywalking.oap.server.receiver.golang.provider;

import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;

public class GolangOALDefine  extends OALDefine {

    public static final GolangOALDefine INSTANCE = new GolangOALDefine();

    private GolangOALDefine() {
        super(
                "oal/golang-agent.oal",
                "org.apache.skywalking.oap.server.core.source"
        );
    }

}
