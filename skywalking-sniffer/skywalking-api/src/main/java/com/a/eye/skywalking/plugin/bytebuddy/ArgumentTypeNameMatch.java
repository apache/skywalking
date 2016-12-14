package com.a.eye.skywalking.plugin.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Created by wusheng on 2016/12/1.
 */
public class ArgumentTypeNameMatch implements ElementMatcher<MethodDescription> {
    private int index;

    private String argumentTypeName;

    public ArgumentTypeNameMatch(int index, String argumentTypeName) {
        this.index = index;
        this.argumentTypeName = argumentTypeName;
    }

    @Override
    public boolean matches(MethodDescription target) {
        if (target.getParameters().size() > index) {
            return target.getParameters().get(index).getType().asErasure().getName().equals(argumentTypeName);
        }

        return false;
    }
}
