package org.skywalking.apm.plugin.nutz.mvc.define;

public class ControllerInstrumentation extends AbstractControllerInstrumentation {

    public static final String ENHANCE_ANNOTATION = "org.nutz.mvc.annotation.At";

    @Override protected String[] getEnhanceAnnotations() {
        return new String[] {ENHANCE_ANNOTATION};
    }
}
