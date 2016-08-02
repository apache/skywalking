package com.ai.cloud.skywalking.plugin.dubbo;

import java.util.List;

import com.ai.cloud.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.MethodInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;

public class ProtocolFilterBuildChainInterceptor implements StaticMethodsAroundInterceptor{

	@SuppressWarnings("rawtypes")
	@Override
	public void beforeMethod(MethodInvokeContext interceptorContext,
			MethodInterceptResult result) {
		Object[] args = interceptorContext.allArguments();
		final Invoker<?> invoker = (Invoker<?>)args[0];
		String key = (String)args[1];
		String group = (String)args[2];

		final URL newURL = invoker.getUrl().addParameter(key, "skywalking$enhanceFilter");
        Invoker<?> last = invoker;
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(newURL, key, group);
        if (filters.size() > 0) {
            for (int i = filters.size() - 1; i >= 0; i--) {
                final Filter filter = filters.get(i);
                final Invoker<?> next = last;
                last = new Invoker() {
                    public Class<?> getInterface() {
                        return invoker.getInterface();
                    }

                    public URL getUrl() {
                        return newURL;
                    }

                    public boolean isAvailable() {
                        return invoker.isAvailable();
                    }

                    public Result invoke(Invocation invocation) throws RpcException {
                        return filter.invoke(next, invocation);
                    }

                    public void destroy() {
                        invoker.destroy();
                    }

                    @Override
                    public String toString() {
                        return invoker.toString();
                    }
                };
            }
        }
        
        result.defineReturnValue(last);
	}

	@Override
	public Object afterMethod(MethodInvokeContext interceptorContext, Object ret) {
		return null;
		//unreachable
	}

	@Override
	public void handleMethodException(Throwable t,
			MethodInvokeContext interceptorContext, Object ret) {
		//unreachable
	}

}
