package com.a.eye.skywalking.alarm.model;

public class ConfigArgsDescriber {
    private int period;

    private MailInfo mailInfo;

    private UrlInfo urlInfo;

    public int getPeriod() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public MailInfo getMailInfo() {
        return mailInfo;
    }

    public void setMailInfo(MailInfo mailInfo) {
        this.mailInfo = mailInfo;
    }

    public UrlInfo getUrlInfo() {
        return urlInfo;
    }

    public void setUrlInfo(UrlInfo urlInfo) {
        this.urlInfo = urlInfo;
    }
}
