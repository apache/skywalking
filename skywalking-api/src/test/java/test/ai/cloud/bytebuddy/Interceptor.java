package test.ai.cloud.bytebuddy;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

public class Interceptor{
	@RuntimeType
	public Object intercept(@AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> zuper){
		try {
			return "intercept_" + zuper.call();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
}
