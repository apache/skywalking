package com.ai.cloud.skywalking.agent;

import com.ai.cloud.skywalking.agent.junction.SkyWalkingEnhanceMatcher;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.AbstractClassEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.PluginBootstrap;
import com.ai.cloud.skywalking.plugin.PluginDefineCategory;
import com.ai.cloud.skywalking.plugin.PluginException;
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

public class SkyWalkingAgent {
    private static Logger logger = LogManager.getLogger(SkyWalkingAgent.class);

    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        initConfig();
        if (AuthDesc.isAuth()) {
            final PluginDefineCategory pluginDefineCategory =
                    PluginDefineCategory.category(new PluginBootstrap().loadPlugins());

            new AgentBuilder.Default().type(enhanceClassMatcher(pluginDefineCategory).and(not(isInterface())))
                    .transform(new AgentBuilder.Transformer() {
                        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                TypeDescription typeDescription, ClassLoader classLoader) {
                            AbstractClassEnhancePluginDefine pluginDefine =
                                    pluginDefineCategory.findPluginDefine(typeDescription.getTypeName());
                            return pluginDefine.define(typeDescription.getTypeName(), builder);
                        }
                    }).with(new AgentBuilder.Listener() {
                @Override
                public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader,
                        JavaModule module, DynamicType dynamicType) {

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
    }


    private static <T extends NamedElement> ElementMatcher.Junction<T> enhanceClassMatcher(
            PluginDefineCategory pluginDefineCategory) {
        return new SkyWalkingEnhanceMatcher<T>(pluginDefineCategory);
    }

    private static String generateLocationPath() {
        return SkyWalkingAgent.class.getName().replaceAll("\\.", "/") + ".class";
    }


    private static void initConfig() {
        Config.SkyWalking.IS_PREMAIN_MODE = true;
        Config.SkyWalking.AGENT_BASE_PATH = initAgentBasePath();
    }

    private static String initAgentBasePath() {
        try {
            String urlString =
                    SkyWalkingAgent.class.getClassLoader().getSystemClassLoader().getResource(generateLocationPath())
                            .toString();
            urlString = urlString.substring(urlString.indexOf("file:"), urlString.indexOf('!'));
            return new File(new URL(urlString).getFile()).getParentFile().getAbsolutePath();
        } catch (Exception e) {
            logger.error("Failed to init config .", e);
            return "";
        }
    }
}
