package org.skywalking.apm.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.skywalking.apm.agent.junction.SkyWalkingEnhanceMatcher;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.SnifferConfigInitializer;
import org.skywalking.apm.agent.core.logging.EasyLogResolver;
import org.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.PluginBootstrap;
import org.skywalking.apm.agent.core.plugin.PluginException;
import org.skywalking.apm.agent.core.plugin.PluginFinder;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * The main entrance of sky-waking agent,
 * based on javaagent mechanism.
 *
 * @author wusheng
 */
public class SkyWalkingAgent {
    private static final ILog logger;

    static {
        LogManager.setLogResolver(new EasyLogResolver());
        logger = LogManager.getLogger(SkyWalkingAgent.class);
    }

    /**
     * Main entrance.
     * Use byte-buddy transform to enhance all classes, which define in plugins.
     *
     * @param agentArgs
     * @param instrumentation
     * @throws PluginException
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        SnifferConfigInitializer.initialize();

        final PluginFinder pluginFinder = new PluginFinder(new PluginBootstrap().loadPlugins());

        ServiceManager.INSTANCE.boot();

        new AgentBuilder.Default().type(enhanceClassMatcher(pluginFinder).and(not(isInterface()))).transform(new AgentBuilder.Transformer() {
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                    ClassLoader classLoader) {
                AbstractClassEnhancePluginDefine pluginDefine = pluginFinder.find(typeDescription.getTypeName());
                return pluginDefine.define(typeDescription.getTypeName(), builder);
            }
        }).with(new AgentBuilder.Listener() {
            @Override
            public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module,
                                         DynamicType dynamicType) {

            }

            @Override
            public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
            }

            @Override
            public void onError(String typeName, ClassLoader classLoader, JavaModule module, Throwable throwable) {
                logger.error("Failed to enhance class " + typeName, throwable);
            }

            @Override
            public void onComplete(String typeName, ClassLoader classLoader, JavaModule module) {
            }
        }).installOn(instrumentation);
    }

    /**
     * Get the enhance target classes matcher.
     *
     * @param pluginFinder
     * @param <T>
     * @return class matcher.
     */
    private static <T extends NamedElement> ElementMatcher.Junction<T> enhanceClassMatcher(PluginFinder pluginFinder) {
        return new SkyWalkingEnhanceMatcher<T>(pluginFinder);
    }
}
