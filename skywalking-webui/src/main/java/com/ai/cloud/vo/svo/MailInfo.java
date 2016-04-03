package com.ai.cloud.vo.svo;

import java.io.Serializable;

public class MailInfo implements Serializable {
    private String[] mailTo;
    private String[] mailCc;

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
}
