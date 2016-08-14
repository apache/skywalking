package com.a.eye.skywalking.conf;

public class ConfigValidator {
    private ConfigValidator() {
        // Non
    }

    public static boolean validate() {
        if (!validateSendMaxLength()) {
            throw new IllegalArgumentException("Max send length must great than the sum of the maximum " +
                    "length of sending exception stack and the maximum length of sending business key.");
        }

        return true;
    }

    private static boolean validateSendMaxLength() {
        if (Config.Sender.MAX_SEND_LENGTH < (Config.BuriedPoint.MAX_EXCEPTION_STACK_LENGTH +
                Config.BuriedPoint.BUSINESSKEY_MAX_LENGTH)) {
            return false;
        }

        return true;
    }

}
