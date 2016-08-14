package test.a.eye.cloud.bytebuddy;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

public class ConstructorInterceptor {
	@RuntimeType
	public void intercept(@AllArguments Object[] allArguments) {
		System.out
				.println("ConstructorInterceptor size:" + allArguments.length);
		if(allArguments.length > 0){
			System.out.println("ConstructorInterceptor param[0]=" + allArguments[0]);
		}
	}
}
