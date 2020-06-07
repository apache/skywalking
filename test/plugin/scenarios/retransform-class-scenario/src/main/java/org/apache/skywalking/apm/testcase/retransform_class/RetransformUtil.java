package org.apache.skywalking.apm.testcase.retransform_class;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import test.org.apache.skywalking.apm.testcase.controller.TestController;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * @author gongdewei 2020/6/7
 */
public class RetransformUtil {

    private static final Logger logger = LogManager.getLogger(RetransformUtil.class);
    public static final String RETRANSFORMING_TAG = "_retransforming_";
    public static final String RETRANSFORM_VALUE = "hello_from_agent";

    public static void doRetransform() {
        Instrumentation instrumentation = ByteBuddyAgent.install();

        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if (className.endsWith("TestController")) {
                    //replace '_retransforming_' with 'hello_from_agent', both length is 16
                    byte[] bytes = RETRANSFORMING_TAG.getBytes();
                    int offset = indexOf(classfileBuffer, bytes);
                    if (offset != -1) {
                        byte[] replacingBytes = RETRANSFORM_VALUE.getBytes();
                        System.arraycopy(replacingBytes,0, classfileBuffer, offset, replacingBytes.length);
                    }
                    return classfileBuffer;
                }
                return null;
            }
        };

        try {
            instrumentation.addTransformer(transformer, true);
            try {
                instrumentation.retransformClasses(TestController.class);
                logger.info("retransform classes success");
            } catch (Throwable e) {
                logger.error("retransform classes failure", e);
            }

        } finally {
            instrumentation.removeTransformer(transformer);
        }

    }

    private static int indexOf(byte[] outerArray, byte[] smallerArray) {
        for(int i = 0; i < outerArray.length - smallerArray.length+1; ++i) {
            boolean found = true;
            for(int j = 0; j < smallerArray.length; ++j) {
                if (outerArray[i+j] != smallerArray[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
    }
}
