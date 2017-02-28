package com.a.eye.skywalking.agent;

import com.a.eye.skywalking.agent.junction.SkyWalkingEnhanceMatcher;
import com.a.eye.skywalking.api.conf.Config;
import com.a.eye.skywalking.api.conf.SnifferConfigInitializer;
import com.a.eye.skywalking.api.logging.EasyLogResolver;
import com.a.eye.skywalking.api.logging.api.ILog;
import com.a.eye.skywalking.api.logging.api.LogManager;
import com.a.eye.skywalking.api.plugin.AbstractClassEnhancePluginDefine;
import com.a.eye.skywalking.api.plugin.PluginBootstrap;
import com.a.eye.skywalking.api.plugin.PluginFinder;
import com.a.eye.skywalking.api.plugin.PluginException;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URL;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * The main entrance of sky-waking agent.
 * It bases on javaagent mechanism.
 *
 * @author wusheng
 */
public class SkyWalkingAgent {
    static {
        LogManager.setLogResolver(new EasyLogResolver());
    }

    private static ILog logger;

    /**
     * Main entrance.
     * Use byte-buddy transform to enhance all classes, which define in plugins.
     *
     * @param agentArgs
     * @param instrumentation
     * @throws PluginException
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        logger = LogManager.getLogger(SkyWalkingAgent.class);

        initConfig();

        final PluginFinder pluginFinder = new PluginFinder(new PluginBootstrap().loadPlugins());

        new AgentBuilder.Default().type(enhanceClassMatcher(pluginFinder).and(not(isInterface()))).transform(new AgentBuilder.Transformer() {
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader) {
                AbstractClassEnhancePluginDefine pluginDefine = pluginFinder.find(typeDescription.getTypeName());
                return pluginDefine.define(typeDescription.getTypeName(), builder);
            }
        }).with(new AgentBuilder.Listener() {
            @Override
            public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, DynamicType dynamicType) {

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

    private static String generateLocationPath() {
        return SkyWalkingAgent.class.getName().replaceAll("\\.", "/") + ".class";
    }


    private static void initConfig() {
        Config.SkyWalking.IS_PREMAIN_MODE = true;
        Config.SkyWalking.AGENT_BASE_PATH = initAgentBasePath();

        SnifferConfigInitializer.initialize();
    }

    /**
     * Try to allocate the skywalking-agent.jar
     * Some config files or output resources are from this path.
     *
     * @return the path, where the skywalking-agent.jar is.
     */
    private static String initAgentBasePath() {
        try {
            String urlString = SkyWalkingAgent.class.getClassLoader().getSystemClassLoader().getResource(generateLocationPath()).toString();
            urlString = urlString.substring(urlString.indexOf("file:"), urlString.indexOf('!'));
            return new File(new URL(urlString).getFile()).getParentFile().getAbsolutePath();
        } catch (Exception e) {
            logger.error("Failed to init config .", e);
            return "";
        }
    }
}
