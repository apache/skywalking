package com.a.eye.skywalking.api.plugin;

import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.TypeResolutionStrategy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.pool.TypePool;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

/**
 * A test entrance for enhancing class.
 * This should be used only in bytecode-manipulate test.
 * And make sure, all classes which need to be enhanced, must not be loaded.
 *
 * @author wusheng
 */
public class TracingBootstrap {
    private static ILog logger = LogManager.getLogger(TracingBootstrap.class);

    private TracingBootstrap() {
    }

    /**
     * Main entrance for testing.
     * @param args includes target classname ( which exists "public static void main(String[] args)" ) and arguments list.
     * @throws PluginException
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    public static void main(String[] args)
            throws PluginException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException {
        if (args.length == 0) {
            throw new RuntimeException("bootstrap failure. need args[0] to be main class.");
        }

        List<AbstractClassEnhancePluginDefine> plugins = null;

        try {
            PluginBootstrap bootstrap = new PluginBootstrap();
            plugins = bootstrap.loadPlugins();
        } catch (Throwable t) {
            logger.error("PluginBootstrap start failure.", t);
        }

        for (AbstractClassEnhancePluginDefine plugin : plugins) {
            String enhanceClassName = plugin.enhanceClassName();
            TypePool.Resolution resolution = TypePool.Default.ofClassPath().describe(enhanceClassName);
            if (!resolution.isResolved()) {
                logger.error("Failed to resolve the class " + enhanceClassName, null);
                continue;
            }
            DynamicType.Builder<?> newClassBuilder =
                    new ByteBuddy().rebase(resolution.resolve(), ClassFileLocator.ForClassLoader.ofClassPath());
            newClassBuilder = ((AbstractClassEnhancePluginDefine) plugin).define(enhanceClassName, newClassBuilder);
            newClassBuilder.make(new TypeResolutionStrategy.Active()).load(ClassLoader.getSystemClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();
        }

        String[] newArgs = Arrays.copyOfRange(args, 1, args.length);


        Class.forName(args[0]).getMethod("main", String[].class).invoke(null, new Object[] {newArgs});
    }
}
