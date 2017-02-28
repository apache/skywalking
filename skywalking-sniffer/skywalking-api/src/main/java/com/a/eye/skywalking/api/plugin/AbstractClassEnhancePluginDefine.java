package com.a.eye.skywalking.api.plugin;

import com.a.eye.skywalking.api.logging.api.ILog;
import com.a.eye.skywalking.api.logging.api.LogManager;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import com.a.eye.skywalking.api.util.StringUtil;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.pool.TypePool.Resolution;

/**
 * Basic abstract class of all sky-walking auto-instrumentation plugins.
 * <p>
 * It provides the outline of enhancing the target class.
 * If you want to know more about enhancing, you should go to see {@link ClassEnhancePluginDefine}
 */
public abstract class AbstractClassEnhancePluginDefine {
    private static ILog logger = LogManager.getLogger(AbstractClassEnhancePluginDefine.class);

    private TypePool classTypePool;

    /**
     * Main entrance of enhancing the class.
     *
     * @param transformClassName target class.
     * @param builder            byte-buddy's builder to manipulate target class's bytecode.
     * @return be defined builder.
     * @throws PluginException, when set builder failure.
     */
    public DynamicType.Builder<?> define(String transformClassName, DynamicType.Builder<?> builder) throws PluginException {
        String interceptorDefineClassName = this.getClass().getName();

        if (StringUtil.isEmpty(transformClassName)) {
            logger.warn("classname of being intercepted is not defined by {}.", interceptorDefineClassName);
            return builder;
        }

        logger.debug("prepare to enhance class {} by {}.", transformClassName, interceptorDefineClassName);

        /**
         * find witness classes for enhance class
         */
        String[] witnessClasses = witnessClasses();
        if (witnessClasses != null) {
            for (String witnessClass : witnessClasses) {
                Resolution witnessClassResolution = classTypePool.describe(witnessClass);
                if (!witnessClassResolution.isResolved()) {
                    logger.warn("enhance class {} by plugin {} is not working. Because witness class {} is not existed.", transformClassName, interceptorDefineClassName,
                            witnessClass);
                    return builder;
                }
            }
        }

        /**
         * find origin class source code for interceptor
         */
        DynamicType.Builder<?> newClassBuilder = this.enhance(transformClassName, builder);


        logger.debug("enhance class {} by {} completely.", transformClassName, interceptorDefineClassName);

        return newClassBuilder;
    }

    protected abstract DynamicType.Builder<?> enhance(String enhanceOriginClassName, DynamicType.Builder<?> newClassBuilder) throws PluginException;

    /**
     * Define the classname of target class.
     *
     * @return class full name.
     */
    protected abstract String enhanceClassName();

    /**
     * Witness classname list.
     * Why need witness classname? Let's see like this:
     * A library existed two released versions (like 1.0, 2.0), which include the same target classes,
     * but because of version iterator, they may have the same name, but different methods, or different method arguments list.
     * So,
     * if I want to target the particular version (let's say 1.0 for example), version number is obvious not an option，
     * this is the moment you need "Witness classes".
     * You can add any classes only in this particular release version ( something like class com.company.1.x.A, only in 1.0 ),
     * and you can achieve the goal.
     *
     * @return
     */
    protected String[] witnessClasses() {
        return new String[] {};
    }

    public void setClassTypePool(TypePool classTypePool) {
        this.classTypePool = classTypePool;
    }
}
