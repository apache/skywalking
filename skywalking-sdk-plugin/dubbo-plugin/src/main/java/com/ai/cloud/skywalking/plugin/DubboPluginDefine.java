package com.ai.cloud.skywalking.plugin;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.plugin.boot.BootException;
import com.ai.cloud.skywalking.plugin.boot.BootPluginDefine;
import com.alibaba.dubbo.rpc.Invoker;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.pool.TypePool;

import static com.ai.cloud.skywalking.plugin.PluginBootstrap.CLASS_TYPE_POOL;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class DubboPluginDefine extends BootPluginDefine {

    private static final String interceptorClassName = "com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper";

    @Override
    protected void boot() throws BootException {
        if (!AuthDesc.isAuth()) {
            return;
        }
        
        TypePool.Resolution resolution = CLASS_TYPE_POOL.describe(interceptorClassName);
        DynamicType.Builder<?> newClassBuilder = new ByteBuddy()
                .rebase(resolution.resolve(),
                        ClassFileLocator.ForClassLoader.ofClassPath());

        newClassBuilder = newClassBuilder.method(named("buildInvokerChain")
                .and(takesArguments(Invoker.class, String.class, String.class)))
                .intercept(MethodDelegation.to(new DubboFilterBuildInterceptor()));

        newClassBuilder
                .name(interceptorClassName)
                .make()
                .load(ClassLoader.getSystemClassLoader(),
                        ClassLoadingStrategy.Default.INJECTION).getLoaded();
    }
}
