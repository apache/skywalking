package org.skywalking.apm.plugin.nutz.http.sync.define;

import org.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.skywalking.apm.agent.core.plugin.match.NameMatch;

public class NutzHttpFilePostSenderInstrumentation extends AbstractNutzHttpInstrumentation {

    protected ClassMatch enhanceClass() {
        return NameMatch.byName("org.nutz.http.sender.FilePostSender");
    }
}
