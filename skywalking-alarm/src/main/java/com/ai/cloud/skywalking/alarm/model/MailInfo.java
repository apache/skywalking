package com.ai.cloud.skywalking.alarm.model;

public class MailInfo {
    private String[] mailTo;

    private String[] mailCc;

    private String mailTemp;

    public String[] getMailTo() {
        return mailTo;
    }

    public void setMailTo(String[] mailTo) {
        this.mailTo = mailTo;
    }

    public String[] getMailCc() {
        return mailCc;
    }

    public void setMailCc(String[] mailCc) {
        this.mailCc = mailCc;
    }

    public String getMailTemp() {
        return mailTemp;
    }

    public void setMailTemp(String mailTemp) {
        this.mailTemp = mailTemp;
    }
}
