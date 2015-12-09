package com.ai.cloud.skywalking.alarm.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class MailUtil {

    private static Logger logger = LogManager.getLogger(MailUtil.class);

    private static Session session;
    private static String sendAccount;
    private static Transport ts;

    static {
        try {
            Properties prop = new Properties();
            prop.load(MailUtil.class.getResourceAsStream("/mail/mail.config"));
            session = Session.getInstance(prop);
            ts = session.getTransport();
            ts.connect(prop.getProperty("mail.host"), prop.getProperty("mail.username"), prop.getProperty("mail.password"));
            sendAccount = prop.getProperty("mail.username") + prop.getProperty("mail.account.prefix");
        } catch (Exception e) {
            logger.error("Failed to connect the mail System.", e);
            System.exit(-1);
        }
    }


    public static void sendMail(String[] recipientAccounts, String[] ccList, String context, String title) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(sendAccount));
            InternetAddress[] recipientAccountArray = new InternetAddress[recipientAccounts.length];
            for (int i = 0; i < recipientAccounts.length; i++) {
                recipientAccountArray[i] = new InternetAddress(recipientAccounts[i]);
            }
            message.addRecipients(Message.RecipientType.TO, recipientAccountArray);

            InternetAddress[] ccAccountArray = new InternetAddress[ccList.length];
            for (int i = 0; i < recipientAccounts.length; i++) {
                ccAccountArray[i] = new InternetAddress(ccList[i]);
            }
            message.addRecipients(Message.RecipientType.CC, ccAccountArray);

            message.setSubject(title);
            message.setContent(context, "text/html;charset=UTF-8");

            ts.sendMessage(message, message.getAllRecipients());
        } catch (AddressException e) {
            logger.error("Recipient Account is not correct.", e);
        } catch (NoSuchProviderException e) {
            logger.error("Failed to send mail.", e);
        } catch (MessagingException e) {
            logger.error("Failed to send mail.", e);
        }
    }


}
