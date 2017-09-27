package org.skywalking.apm.plugin.xmemcached.v2.define;

import static org.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 *@author IluckySi
 * 
 */
public class XMemcachedInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

	 private static final ILog logger = LogManager.getLogger(XMemcachedInstrumentation.class);
	 
    private static final String ENHANCE_CLASS = "net.rubyeye.xmemcached.XMemcachedClient";
    
    /**
     * public XMemcachedClient(final String host, final int port, int weight) throws IOException
     */
    private static final String CONSTRUCTOR_WITH_HOSTPORT_ARG_INTERCEPT_CLASS = "org.skywalking.apm.plugin.xmemcached.v2.XMemcachedConstructorWithHostPortArgInterceptor";

    /**
     * public XMemcachedClient(final InetSocketAddress inetSocketAddress, int weight) throws IOException {
     */
    private static final String CONSTRUCTOR_WITH_INETSOCKETADDRESS_ARG_INTERCEPT_CLASS = "org.skywalking.apm.plugin.xmemcached.v2.XMemcachedConstructorWithInetSocketAddressArgInterceptor";
    
    /**
     * public XMemcachedClient(List<InetSocketAddress> addressList) throws IOException {
     */
    private static final String CONSTRUCTOR_WITH_INETSOCKETADDRESS_LIST_ARG_INTERCEPT_CLASS = "org.skywalking.apm.plugin.xmemcached.v2.XMemcachedConstructorWithInetSocketAddressListArgInterceptor";
    
    /**
     * XMemcachedClient(MemcachedSessionLocator locator, BufferAllocator allocator, Configuration conf, Map<SocketOption, Object> socketOptions,
     * 		CommandFactory commandFactory, Transcoder transcoder, Map<InetSocketAddress, InetSocketAddress> addressMap,
     *		List<MemcachedClientStateListener> stateListeners, Map<InetSocketAddress, AuthInfo> map, int poolSize,
     *		long connectTimeout, String name, boolean failureMode) throws IOException {
     */
    private static final String CONSTRUCTOR_WITH_COMPLEX_ARG_INTERCEPT_CLASS = "org.skywalking.apm.plugin.xmemcached.v2.XMemcachedConstructorWithComplexArgInterceptor";
    
    private static final String METHOD_INTERCEPT_CLASS = "org.skywalking.apm.plugin.xmemcached.v2.XMemcachedMethodInterceptor";

    @Override
    public ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                	 return takesArguments(String.class, int.class);
                }

				@Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_HOSTPORT_ARG_INTERCEPT_CLASS;
                }
            },
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgument(0, InetSocketAddress.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_INETSOCKETADDRESS_ARG_INTERCEPT_CLASS;
                }
            },
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgument(0, List.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_INETSOCKETADDRESS_LIST_ARG_INTERCEPT_CLASS;
                }
            },
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgument(6, Map.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_COMPLEX_ARG_INTERCEPT_CLASS;
                }
            }
        };
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                	 return // named("sendCommand");
                			 named("gets").or(named("set")) .or(named("add")).or(named("replace"))
                			 .or(named("append")) .or(named("prepend")).or(named("cas")).or(named("delete")).or(named("touch")).
                			 or(named("getAndTouch")).or(named("incr")) .or(named("decr"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return METHOD_INTERCEPT_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
