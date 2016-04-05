package com.ai.cloud.skywalking.web.bo;

import java.io.Serializable;

public class ConfigArgs implements Serializable {

    private int period;

    private MailInfo mailInfo;

    public MailInfo getMailInfo() {
        return mailInfo;
    }

    public void setMailInfo(MailInfo mailInfo) {
        this.mailInfo = mailInfo;
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    @Override
    public String toString() {
        return "ConfigArgs{" +
                "period=" + period +
                ", mailInfo=" + mailInfo +
                '}';
    }
}
