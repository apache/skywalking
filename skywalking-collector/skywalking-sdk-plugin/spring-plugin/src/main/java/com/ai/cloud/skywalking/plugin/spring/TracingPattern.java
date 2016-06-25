package com.ai.cloud.skywalking.plugin.spring;

import org.springframework.aop.aspectj.AspectJExpressionPointcut;

public class TracingPattern {
    private String packageExpression = "";
    private String classExpression = "";
    private AspectJExpressionPointcut packageMatcher;
    private AspectJExpressionPointcut pointcut;

    public String getPackageExpression() {
        return packageExpression;
    }

    public void setPackageExpression(String packageExpression) {
        this.packageExpression = packageExpression;
    }

    public String getClassExpression() {
        return classExpression;
    }

    public void setClassExpression(String classExpression) {
        this.classExpression = classExpression;
    }

    public AspectJExpressionPointcut getPackageMatcher() {
        return packageMatcher;
    }

    public void setPackageMatcher(AspectJExpressionPointcut packageMatcher) {
        this.packageMatcher = packageMatcher;
    }

    public AspectJExpressionPointcut getPointcut() {
        return pointcut;
    }

    public void setPointcut(AspectJExpressionPointcut pointcut) {
        this.pointcut = pointcut;
    }
}
