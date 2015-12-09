package com.ai.cloud.skywalking.alarm.procesor;

import com.ai.cloud.skywalking.alarm.model.AlarmRule;
import com.ai.cloud.skywalking.alarm.model.ApplicationInfo;
import com.ai.cloud.skywalking.alarm.model.UserInfo;
import com.ai.cloud.skywalking.alarm.model.parameter.Application;
import com.ai.cloud.skywalking.alarm.util.MailUtil;
import com.ai.cloud.skywalking.alarm.util.RedisUtil;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class AlarmMessageProcessor {

    private static Logger logger = LogManager.getLogger(AlarmMessageProcessor.class);

    public static void process(UserInfo userInfo, AlarmRule rule) {
        Set<String> sentData = new HashSet<String>();
        List<Application> applications = new ArrayList<Application>();
        Set<String> toBeSendData;
        Application temApplication;
        long currentFireTimeM = System.currentTimeMillis() / (10000 * 6);
        // 获取待发送数据
        if (checkerProcessInterval(rule, currentFireTimeM)) {
            for (ApplicationInfo applicationInfo : rule.getApplicationInfos()) {
                toBeSendData = new HashSet<String>();

                for (int i = 0; i < currentFireTimeM - rule.getPreviousFireTimeM(); i++) {
                    toBeSendData.addAll(getAlarmMessage(generateAlarmKey(userInfo.getUserId(),
                            applicationInfo.getAppCode(), i)));

                    toBeSendData.removeAll(sentData);
                    sentData.addAll(toBeSendData);
                }

                temApplication = new Application(applicationInfo.getAppId());
                temApplication.setTraceIds(toBeSendData);
                applications.add(temApplication);
            }

            // 没有数据需要发送
            if (sentData.size() <= 0) {
                // 清理数据
                for (ApplicationInfo applicationInfo : rule.getApplicationInfos()) {
                    for (int i = 0; i < currentFireTimeM - rule.getPreviousFireTimeM(); i++) {
                        expiredAlarmMessage(generateAlarmKey(userInfo.getUserId(),
                                applicationInfo.getAppCode(), i));
                    }
                }
                // 修改-保存上次处理时间
                rule.setPreviousFireTimeM(currentFireTimeM);
                savePreviousFireTime(userInfo.getUserId(), rule.getRuleId(), currentFireTimeM);
            } else {
                if ("0".equals(rule.getTodoType())) {
                    // 发送邮件
                    String subjects = generateSubjects(sentData.size(),
                            rule.getPreviousFireTimeM(), currentFireTimeM);
                    Map parameter = new HashMap();
                    parameter.put("applications", applications);
                    parameter.put("name", userInfo.getUserId());
                    MailUtil.sendMail(rule.getConfigArgsDescriber().getMailInfo().getMailTo(),
                            rule.getConfigArgsDescriber().getMailInfo().getMailCc(),
                            generateContent(rule.getConfigArgsDescriber().getMailInfo()
                                    .getMailTemp(), parameter),
                            subjects);
                }
                // 清理数据
                for (ApplicationInfo applicationInfo : rule.getApplicationInfos()) {
                    for (int i = 0; i < currentFireTimeM - rule.getPreviousFireTimeM(); i++) {
                        expiredAlarmMessage(generateAlarmKey(userInfo.getUserId(),
                                applicationInfo.getAppCode(), i));
                    }
                }
                // 修改-保存上次处理时间
                rule.setPreviousFireTimeM(currentFireTimeM);
                savePreviousFireTime(userInfo.getUserId(), rule.getRuleId(), currentFireTimeM);
            }
        }


    }

    private static String generateSubjects(int count, long startTime, long endTime) {
        String title = "[Warning] There were " + count + "  alarm information between " +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(startTime)) +
                " to " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(endTime));

        return title;
    }

    private static boolean checkerProcessInterval(AlarmRule rule, long currentFireTimeM) {
        return currentFireTimeM - rule.getPreviousFireTimeM() >= rule.getConfigArgsDescriber().getPeriod();
    }

    private static void expiredAlarmMessage(String key) {
        Jedis client = RedisUtil.getRedisClient();
        client.expire(key, 0);
        if (client != null) {
            client.close();
        }
    }


    private static void savePreviousFireTime(String userId, String ruleId, long currentFireTimeM) {
        Jedis client = RedisUtil.getRedisClient();
        client.hset(userId, ruleId, String.valueOf(currentFireTimeM));
        if (client != null) {
            client.close();
        }
    }


    private static String generateAlarmKey(String userId, String appCode, int period) {
        return userId + "-" + appCode + "-" + ((System.currentTimeMillis() / (10000 * 6))
                - period);
    }

    private static Collection<String> getAlarmMessage(String key) {
        Jedis client = RedisUtil.getRedisClient();
        Map<String, String> result = client.hgetAll(key);
        if (result == null) {
            return new ArrayList<String>();
        }

        client.close();

        return result.values();
    }

    private static String generateContent(String templateStr, Map parameter) {
        Configuration cfg = new Configuration(new Version("2.3.23"));
        cfg.setDefaultEncoding("UTF-8");
        Template t = null;
        try {
            t = new Template(null, new StringReader(templateStr), cfg);
            StringWriter out = new StringWriter();
            t.process(parameter, out);
            return out.getBuffer().toString();
        } catch (IOException e) {
            logger.error("Template illegal.", e);
        } catch (TemplateException e) {
            logger.error("Failed to generate content.", e);
        }

        return "";
    }
}
