package com.ai.cloud.skywalking.plugin;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.pool.TypePool;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

/**
 * 替代应用函数的main函数入口，确保在程序入口处运行 <br/>
 * 用于替代-javaagent的另一种模式 <br/>
 *
 * @author wusheng
 */
public class TracingBootstrap {
    private static Logger logger = LogManager.getLogger(TracingBootstrap.class);

    private TracingBootstrap() {
    }

    public static void main(String[] args) throws PluginException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (args.length == 0) {
            throw new RuntimeException("bootstrap failure. need args[0] to be main class.");
        }

        List<IPlugin> plugins = null;

        try {
            PluginBootstrap bootstrap = new PluginBootstrap();
            plugins = bootstrap.loadPlugins();
        } catch (Throwable t) {
            logger.error("PluginBootstrap start failure.", t);
        }

        for (IPlugin plugin : plugins) {
            DynamicType.Builder<?> newClassBuilder = null;

            if (plugin instanceof AbstractClassEnhancePluginDefine) {
                String enhanceClassName = ((AbstractClassEnhancePluginDefine) plugin).enhanceClassName();
                TypePool.Resolution resolution = TypePool.Default.ofClassPath().describe(enhanceClassName);
                if (!resolution.isResolved()) {
                    logger.error("Failed to resolve the class " + enhanceClassName);
                    continue;
                }
                newClassBuilder = new ByteBuddy().rebase(resolution.resolve(), ClassFileLocator.ForClassLoader.ofClassPath());
            }

            plugin.define(newClassBuilder);

        }

        String[] newArgs = Arrays.copyOfRange(args, 1, args.length);


        Class.forName(args[0]).getMethod("main", String[].class).invoke(null, new Object[] {newArgs});
    }
}
