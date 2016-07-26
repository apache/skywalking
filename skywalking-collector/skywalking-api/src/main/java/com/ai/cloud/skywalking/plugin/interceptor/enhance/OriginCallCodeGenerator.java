package com.ai.cloud.skywalking.plugin.interceptor.enhance;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class OriginCallCodeGenerator {
    private static Logger logger = LogManager.getLogger(OriginCallCodeGenerator.class);
    private static String staticMethodOriginCallCode;
    private static String instanceMethodOriginCallCode;

    static {
        try {
            instanceMethodOriginCallCode = loadInstanceMethodOriginCallCode();
            staticMethodOriginCallCode = loadStaticMethodOriginCallCode();
        } catch (Exception e) {

        }
    }

    private static String loadInstanceMethodOriginCallCode() throws IOException {
        return loadCodeSegment("/instance_method_call_origin_code.conf");
    }

    private static String loadStaticMethodOriginCallCode() throws IOException {
        return loadCodeSegment("/static_method_call_origin_code.conf");
    }

    private static String loadCodeSegment(String fileName) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(OriginCallCodeGenerator.class.getResourceAsStream(fileName)));
        StringBuilder code = new StringBuilder();
        String codeSegment;
        while ((codeSegment = bufferedReader.readLine()) != null) {
            code.append(codeSegment);
        }

        return code.toString();
    }

    public static String generateInstanceMethodOriginCallCode(String originObject, String methodName) {
        return instanceMethodOriginCallCode.toString().replaceAll("%origin_object%", originObject).replaceAll("%method_name%", methodName);
    }

    public static String generateStaticMethodOriginCallCode(String className, String methodName) {
        return staticMethodOriginCallCode.toString().replaceAll("%class_name%", className).replaceAll("%method_name%", methodName);
    }

}
