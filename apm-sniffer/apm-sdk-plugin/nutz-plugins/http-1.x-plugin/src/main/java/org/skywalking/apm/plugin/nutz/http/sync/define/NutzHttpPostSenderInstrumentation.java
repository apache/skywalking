package org.skywalking.apm.plugin.nutz.http.sync.define;

import org.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.skywalking.apm.agent.core.plugin.match.NameMatch;


public class NutzHttpPostSenderInstrumentation extends AbstractNutzHttpInstrumentation {

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName("org.nutz.http.sender.PostSender");
    }
}
