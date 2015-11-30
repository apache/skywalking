package com.ai.cloud.skywalking.example.mail.util;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Properties;

public class SendeUtil {

    @Tracing
    public static boolean sendeMail(String recipientAccount) {
        try {
            Properties prop = new Properties();
            prop.load(SendeUtil.class.getResourceAsStream("/mail/mail.config"));
            Session session = Session.getInstance(prop);
            session.setDebug(true);
            Transport ts = session.getTransport();
            ts.connect(prop.getProperty("mail.host"), prop.getProperty("mail.username"), prop.getProperty("mail.password"));
            Message message = createSimpleMail(session, prop.getProperty("mail.username") +
                    prop.getProperty("mail.account.prefix"), recipientAccount);
            ts.sendMessage(message, message.getAllRecipients());
            ts.close();
            return true;
        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static MimeMessage createSimpleMail(Session session, String senderAccount, String recipientAccount) throws MessagingException {
        //创建邮件对象
        MimeMessage message = new MimeMessage(session);
        //指明邮件的发件人
        message.setFrom(new InternetAddress(senderAccount));
        //指明邮件的收件人，现在发件人和收件人是一样的，那就是自己给自己发
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientAccount));
        //邮件的标题
        message.setSubject("注册成功");
        //邮件的文本内容
        message.setContent("你好啊！", "text/html;charset=UTF-8");
        //返回创建好的邮件对象
        return message;
    }
}
