package com.ai.cloud.skywalking.plugin;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.rpc.*;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import java.util.List;

public class DubboFilterBuildInterceptor {

    @RuntimeType
    public <T> Object intercept(
            @Argument(0) final Invoker invoker,
            @Argument(1) final String key,
            @Argument(2) final String group)
            throws Exception {
        final URL newURL = invoker.getUrl().addParameter(key, "skywalking$enhanceFilter");
        Invoker<T> last = invoker;
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(newURL, key, group);
        if (filters.size() > 0) {
            for (int i = filters.size() - 1; i >= 0; i--) {
                final Filter filter = filters.get(i);
                final Invoker<T> next = last;
                last = new Invoker<T>() {
                    public Class<T> getInterface() {
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
        return last;
    }
}
