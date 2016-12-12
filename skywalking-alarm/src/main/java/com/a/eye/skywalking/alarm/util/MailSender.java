package com.a.eye.skywalking.alarm.util;

import com.a.eye.skywalking.alarm.conf.Config;
import com.sun.mail.util.MailSSLSocketFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MailSender {

    private static Logger logger = LogManager.getLogger(MailSender.class);
    private static MailSender sender = new MailSender();
    private String mailSender;
    private Properties config;

    private MailSender() {
        try {
            config = new Properties();

            config.setProperty("mail.transport.protocol", Config.MailSenderInfo.TRANSPORT_PROTOCOL);
            config.setProperty("mail.smtp.auth", String.valueOf(Config.MailSenderInfo.SMTP_AUTH));
            config.setProperty("mail.smtp.socketFactory.port", "587");
            config.setProperty("mail.debug", "true");
            //config.setProperty("mail.smtp.ssl.enable", "true");
            if (Config.MailSenderInfo.SSL_ENABLE) {
                MailSSLSocketFactory sf = new MailSSLSocketFactory();
                sf.setTrustAllHosts(true);
                config.put("mail.smtp.ssl.enable", "true");
                config.put("mail.smtp.ssl.socketFactory", sf);
            }
        } catch (Exception e) {
            logger.error("Failed to load mail sender info.", e);
            System.exit(-1);
        }

        mailSender = Config.MailSenderInfo.SENDER;
    }


    public static void send(String[] recipientAccounts, String[] ccList, String content, String title) {
        Session session = Session.getInstance(sender.config);
        Transport ts = null;
        try {
            ts = session.getTransport();
            ts.connect(Config.MailSenderInfo.MAIL_HOST, Config.MailSenderInfo.USERNAME, Config.MailSenderInfo.PASSWORD);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(sender.mailSender));
            InternetAddress[] recipientAccountArray = new InternetAddress[recipientAccounts.length];
            for (int i = 0; i < recipientAccounts.length; i++) {
                recipientAccountArray[i] = new InternetAddress(recipientAccounts[i]);
            }
            message.addRecipients(Message.RecipientType.TO, recipientAccountArray);
            if (ccList != null && ccList.length > 0) {
               List<InternetAddress> ccAccountArray = new ArrayList<InternetAddress>();
                for (int i = 0; i < ccList.length; i++) {
                    if (ccList[i] != null && ccList[i].length() > 0)
                        ccAccountArray.add(new InternetAddress(ccList[i]));
                }
                if (ccAccountArray.size() > 0) {
                    message.addRecipients(Message.RecipientType.CC, ccAccountArray.toArray(new InternetAddress[ccAccountArray.size()]));
                }
            }
            message.setSubject(title);
            message.setContent(content, "text/html;charset=UTF-8");
            ts.sendMessage(message, message.getAllRecipients());

        } catch (AddressException e) {
            logger.error("Recipient Account is not correct.", e);
        } catch (NoSuchProviderException e) {
            logger.error("Failed to send mail.", e);
        } catch (MessagingException e) {
            logger.error("Failed to send mail.", e);
        } finally {
            if (ts != null) {
                try {
                    ts.close();
                } catch (MessagingException e) {
                    logger.error("Failed to close transport.", e);
                }
            }
        }
    }


}
