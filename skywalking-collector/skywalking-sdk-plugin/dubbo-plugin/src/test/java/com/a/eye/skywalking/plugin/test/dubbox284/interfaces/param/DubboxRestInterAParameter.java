package com.a.eye.skywalking.plugin.test.dubbox284.interfaces.param;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DubboxRestInterAParameter {

    public DubboxRestInterAParameter() {
    }

    @XmlElement(name = "parameterA")
    private String parameterA;

    public DubboxRestInterAParameter(String parameterA) {
        this.parameterA = parameterA;
    }

    public String getParameterA() {
        return parameterA;
    }

    public void setParameterA(String parameterA) {
        this.parameterA = parameterA;
    }
}
