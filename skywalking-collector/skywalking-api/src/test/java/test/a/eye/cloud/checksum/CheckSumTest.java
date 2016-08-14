package test.a.eye.cloud.checksum;

import org.junit.Test;

public class CheckSumTest {
    private static final int dataIndex = 2;
    private static final int MAX_TEST_COUNT = 100_000_00;

    @Test
    public void TestAllXORSum() {
        String data = dataArray[dataIndex];
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < MAX_TEST_COUNT; i++) {
            intToBytes2(makeChecksum(data, 1));
        }
        System.out.println("All XOR check sum  totalSize:" + MAX_TEST_COUNT + " cost :" + (((System.currentTimeMillis() - startTime))));
    }

    @Test
    public void Test18XORSum() {
        String data = dataArray[dataIndex];
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < MAX_TEST_COUNT; i++) {
            intToBytes2(makeChecksum(data, 8));
        }
        System.out.println("All XOR check sum  totalSize:" + MAX_TEST_COUNT + " cost :" + (((System.currentTimeMillis() - startTime))));
    }

    @Test
    public void Test116XORSum() {
        String data = dataArray[dataIndex];
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < MAX_TEST_COUNT; i++) {
            intToBytes2(makeChecksum(data, 16));
        }
        System.out.println("All XOR check sum  totalSize:" + MAX_TEST_COUNT + " cost :" + (((System.currentTimeMillis() - startTime))));
    }

    public int makeChecksum(String data, int step) {
        char[] dataArray = data.toCharArray();
        int result = dataArray[0];
        for (int i = 0; i < dataArray.length; i = i + step) {
            result ^= dataArray[i];
        }

        return result;
    }

    public byte[] intToBytes2(int value) {
        byte[] src = new byte[4];
        src[0] = (byte) ((value >> 24) & 0xFF);
        src[1] = (byte) ((value >> 16) & 0xFF);
        src[2] = (byte) ((value >> 8) & 0xFF);
        src[3] = (byte) (value & 0xFF);
        return src;
    }

    private static final String[] dataArray = new String[]{
            "1.0b.1463530404744.9576be7.22045.45.1480@~0@~0@~com.ai.aisse.controller.overtimeexpense.OvertimeExpenseC" +
                    "ontroller.overtimeInit(com.ai.net.xss.wrapper.XssRequestWrapper,org.apache.catalina.connector.ResponseFa" +
                    "cade,org.springframework.validation.support.BindingAwareModelMap)@~1463530404774@~5@~ITSC-MIS-LEV-web01/" +
                    "10.1.31.12@~0@~ @~M@~false@~ @~22045@~aisse-mobile-web@~5@~L#&",
            "1.0b.1463539216140.50f8123.14804.265333.1493@~0.0.0@~3@~tracing:jdbc:oracle:thin:@10.1.1.61:1521:OAPROD(" +
                    "aisse)@~1463539212130@~1@~ITSC-MIS-LEV-web01/10.1.31.12@~0@~ @~J@~false@~connection.commit@~10872@~aisse" +
                    "-dubbo@~5@~L#&1.0b.1463539217412.50f8123.14804.52.1556@~0.0.0@~1@~com.ai.aisse.core.dao.impl.AisseItemLi" +
                    "stTDaoImpl.queryAisseItemWorkMealTs(java.util.LinkedHashMap)@~1463539213376@~6@~ITSC-MIS-LEV-web01/10.1." +
                    "31.12@~0@~ @~M@~false@~ @~10872@~aisse-dubbo@~5@~L#&1.0b.1463539217412.50f8123.14804.52.1556@~0.0.0@~2@~" +
                    "tracing:jdbc:oracle:thin:@10.1.1.61:1521:OAPROD(aisse)@~1463539213384@~1@~ITSC-MIS-LEV-web01/10.1.31.12@" +
                    "~0@~ @~J@~false@~connection.commit@~10872@~aisse-dubbo@~5@~L#&1.0b.1463539217412.50f8123.14804.52.1556@~" +
                    "0.0@~0@~rest://10.1.31.12:20188/aisse/com.ai.aisse.core.rest.IAisseVoucherApi.queryAisseItemT(Map)@~1463" +
                    "539213354@~32@~ITSC-MIS-LEV-web01/10.1.31.12@~0@~ @~D@~true@~ @~10872@~aisse-dubbo@~5@~S#&",
            "1.0b.1463540545212.3627470.24702.142.5@~ @~0@~Map com.ai.saas.comment.core.service.impl.EvalutionObjSvIm" +
                    "pl.getCommentsParamters(RequestData)@~1463540545212@~32@~host-10-1-236-126/127.0.0.1@~1@~java.lang.Runti" +
                    "meException: Value for ntAccount cannot be null#~       at com.ai.saas.comment.core.model.dto.EvalutionO" +
                    "bjectResultCriteria$GeneratedCriteria.addCriterion(EvalutionObjectResultCriteria.java:116)#~    at com.a" +
                    "i.saas.comment.core.model.dto.EvalutionObjectResultCriteria$GeneratedCriteria.andNtAccountEqualTo(Evalut" +
                    "ionObjectResultCriteria.java:479)#~     at com.ai.saas.comment.core.model.dto.EvalutionObjectResultCrite" +
                    "ria$Criteria.andNtAccountEqualTo(EvalutionObjectResultCriteria.java:1089)#~     at com.ai.saas.comment.c" +
                    "ore.service.impl.EvalutionObjSvImpl.getCommentsParamters(EvalutionObjSvImpl.java:327)#~ at com.ai.saas.c" +
                    "omment.core.service.impl.EvalutionObjSvImpl$$FastClassBySpringCGLIB$$e4328417.invoke(<generated>)#~" +
                    "at org.springframework.cglib.proxy.MethodProxy.invoke(MethodProxy.java:204)#~   at org.springframework.a" +
                    "op.framework.CglibAopProxy$CglibMethodInvocation.invokeJoinpoint(CglibAopProxy.java:720)#~      at org.s" +
                    "pringframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:157)#~" +
                    "at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(Transactio" +
                    "nInterceptor.java:99)#~ at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWi" +
                    "thinTransaction(TransactionAspectSupport.java:281)#~    at org.springframework.transaction.interceptor.T" +
                    "ransactionInterceptor.invoke(TransactionInterceptor.java:96)#~  at org.springframework.aop.framework.Ref" +
                    "lectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:179)#~  at org.springframework.aop.frame" +
                    "work.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:655)#~        at com.ai.saas.c" +
                    "omment.core.service.impl.EvalutionObjSvImpl$$EnhancerBySpringCGLIB$$9d683df1.getCommentsParamters(<gener" +
                    "ated>)#~        at com.ai.saas.comment.core.service.impl.EvalutionObjSvImpl$$FastClassBySpringCGLIB$$e43" +
                    "28417.invoke(<generated>)#~     at org.springframework.cglib.proxy.MethodProxy.invoke(MethodProxy.java:2" +
                    "04)#~   at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.invokeJoinpoint(CglibAo" +
                    "pProxy.java:720)#~      at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:157)#~  at org.springframework.aop.aspectj.MethodInvocationProceedingJoinPoint.p" +
                    "roceed(MethodInvocationProceedingJoinPoint.java:85)#~   at com.a.eye.skywalking.plugin.spring.Tracing" +
                    "Aspect.doTracing(TracingAspect.java:13)#~       at sun.reflect.GeneratedMethodAccessor79.invoke(Unknown " +
                    "Source)#~       at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)" +
                    "#~      at java.lang.reflect.Method.invoke(Method.java:606)#~   at org.springframework.aop.aspectj.Abstr" +
                    "actAspectJAdvice.invokeAdviceMethodWithGivenArgs(AbstractAspectJAdvice.java:621)#~      at org.springfra" +
                    "mework.aop.aspectj.AbstractAspectJAdvice.invokeAdviceMethod(AbstractAspectJAdvice.java:610)#~   at org.s" +
                    "pringframework.aop.aspectj.AspectJAroundAdvice.invoke(AspectJAroundAdvice.java:68)#~    at org.springfra" +
                    "mework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:179)#~  at org.s" +
                    "pringframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:655)#~" +
                    "        at com.ai.saas.comment.core.service.impl.EvalutionObjSvImpl$$EnhancerBySpringCGLIB$$312c8477.get" +
                    "CommentsParamters(<generated>)#~        at com.ai.saas.comment.core.api.impl.ObjectCommentApiImpl.getCom" +
                    "mentsParamters(ObjectCommentApiImpl.java:43)#~  at com.alibaba.dubbo.common.bytecode.Wrapper5.invokeMeth" +
                    "od(Wrapper5.java)#~     at com.alibaba.dubbo.rpc.proxy.javassist.JavassistProxyFactory$1.doInvoke(Javass" +
                    "istProxyFactory.java:46)#~      at com.alibaba.dubbo.rpc.proxy.AbstractProxyInvoker.invoke(AbstractProxy" +
                    "Invoker.java:72)#~      at com.alibaba.dubbo.rpc.protocol.InvokerWrapper.invoke(InvokerWrapper.java:53)#" +
                    "~       at com.alibaba.dubbo.rpc.filter.ExceptionFilter.invoke(ExceptionFilter.java:64)#~       at com.a" +
                    "libaba.dubbo.rpc.protocol.ProtocolFilterWrapper$1.invoke(ProtocolFilterWrapper.java:91)#~       at com.a" +
                    "libaba.dubbo.rpc.filter.TimeoutFilter.invoke(TimeoutFilter.java:42)#~   at com.alibaba.dubbo.rpc.protoco" +
                    "l.Pr@~M@~false@~ @~24702@~saas-comment-servers@~5@~L#&"
    };

}
