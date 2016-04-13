package com.ai.cloud.skywalking.web.dto;

import java.io.Serializable;
import java.util.Arrays;

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

    @Override
    public String toString() {
        return "MailInfo{" +
                "mailTo=" + Arrays.toString(mailTo) +
                ", mailCc=" + Arrays.toString(mailCc) +
                '}';
    }
}
