package org.apache.skywalking.apm.plugin.trace.ignore;

import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.conf.ConfigNotFoundException;
import org.apache.skywalking.apm.agent.core.context.AbstractTracerContext;
import org.apache.skywalking.apm.agent.core.context.ContextManagerExtendService;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.plugin.trace.ignore.conf.IgnoreConfig;
import org.apache.skywalking.apm.plugin.trace.ignore.conf.IgnoreConfigInitializer;
import org.apache.skywalking.apm.plugin.trace.ignore.matcher.AntPathMatcher;
import org.apache.skywalking.apm.plugin.trace.ignore.matcher.TracePathMatcher;
import org.apache.skywalking.apm.util.StringUtil;

/**
 *
 * @author liujc [liujunc1993@163.com]
 *
 */
@OverrideImplementor(ContextManagerExtendService.class)
public class TraceIgnoreExtendService extends ContextManagerExtendService {

    private static final ILog logger = LogManager.getLogger(TraceIgnoreExtendService.class);

    @Override
    public void boot() {
        try {
            IgnoreConfigInitializer.initialize();
        } catch (ConfigNotFoundException e) {
            logger.error("trace ignore config init error", e);
        } catch (AgentPackageNotFoundException e) {
            logger.error("trace ignore config init error", e);
        }
    }


    private TracePathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public AbstractTracerContext createTraceContext(String operationName, boolean forceSampling) {
        String pattens = IgnoreConfig.Trace.IGNORE_PATH;
        if (!StringUtil.isEmpty(pattens) && !forceSampling) {
            String path = operationName;
            if (!StringUtil.isEmpty(path) && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            for (String pattern : pattens.split(",")) {
                if (pathMatcher.match(pattern, path)) {
                    logger.debug("operationName : " + operationName + " Ignore tracking");
                    return new IgnoredTracerContext();
                }
            }
        }
        return super.createTraceContext(operationName, forceSampling);
    }
}
