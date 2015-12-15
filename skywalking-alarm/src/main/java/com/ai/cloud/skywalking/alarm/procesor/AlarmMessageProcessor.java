package com.ai.cloud.skywalking.alarm.procesor;

import com.ai.cloud.skywalking.alarm.dao.AlarmMessageDao;
import com.ai.cloud.skywalking.alarm.model.AlarmRule;
import com.ai.cloud.skywalking.alarm.model.ApplicationInfo;
import com.ai.cloud.skywalking.alarm.model.MailInfo;
import com.ai.cloud.skywalking.alarm.model.UserInfo;
import com.ai.cloud.skywalking.alarm.util.MailUtil;
import com.ai.cloud.skywalking.alarm.util.RedisUtil;
import com.ai.cloud.skywalking.alarm.util.TemplateConfigurationUtil;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AlarmMessageProcessor {

    private static Logger logger = LogManager
            .getLogger(AlarmMessageProcessor.class);

    public void process(UserInfo userInfo, AlarmRule rule) throws TemplateException, IOException, SQLException {
        Set<String> warningTracingIds = new HashSet<String>();
        Set<String> warningMessageKeys = new HashSet<String>();
        long currentFireMinuteTime = System.currentTimeMillis() / (10000 * 6);
        long warningTimeWindowSize = currentFireMinuteTime
                - rule.getPreviousFireTimeM();
        // 获取待发送数据
        if (warningTimeWindowSize >= rule.getConfigArgsDescriber().getPeriod()) {
            for (ApplicationInfo applicationInfo : rule.getApplicationInfos()) {
                for (int period = 0; period < warningTimeWindowSize; period++) {
                    String alarmKey = userInfo.getUserId()
                            + "-"
                            + applicationInfo.getAppCode()
                            + "-"
                            + (currentFireMinuteTime - period - 1);

                    warningMessageKeys.add(alarmKey);
                    warningTracingIds.addAll(getAlarmMessages(alarmKey));
                }
            }

            // 发送告警数据
            if (warningTracingIds.size() > 0) {
                if ("0".equals(rule.getTodoType())) {
                    logger.info("A total of {} alarm information needs to be sent {}", warningTracingIds.size(),
                            rule.getConfigArgsDescriber().getMailInfo().getMailTo());
                    // 发送邮件
                    String subjects = generateSubject(warningTracingIds.size(),
                            rule.getPreviousFireTimeM(), currentFireMinuteTime);
                    Map parameter = new HashMap();
                    parameter.put("warningTracingIds", warningTracingIds);
                    parameter.put("name", userInfo.getUserName());
                    parameter.put("startDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(
                            rule.getPreviousFireTimeM() * 10000 * 6)));
                    parameter.put("endDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(
                            currentFireMinuteTime * 10000 * 6)));
                    String mailContext = generateContent(rule
                            .getConfigArgsDescriber().getMailInfo()
                            .getMailTemp(), parameter);
                    if (mailContext.length() > 0) {
                        MailInfo mailInfo = rule.getConfigArgsDescriber()
                                .getMailInfo();
                        MailUtil.sendMail(mailInfo.getMailTo(),
                                mailInfo.getMailCc(), mailContext, subjects);
                    }
                }
            }

            // 清理数据
            for (String toBeRemovedKey : warningMessageKeys) {
                expiredAlarmMessage(toBeRemovedKey);
            }

            // 修改-保存上次处理时间
            dealPreviousFireTime(userInfo, rule, currentFireMinuteTime);
        }

    }

    private void dealPreviousFireTime(UserInfo userInfo, AlarmRule rule,
                                      long currentFireMinuteTime) {
        rule.setPreviousFireTimeM(currentFireMinuteTime);
        savePreviousFireTime(userInfo.getUserId(), rule.getRuleId(),
                currentFireMinuteTime);
    }

    private String generateSubject(int count, long startTime, long endTime) {
        String title = "[Warning] There were "
                + count
                + "  alarm information between "
                + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(
                startTime * 10000 * 6))
                + " to "
                + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(
                endTime * 10000 * 6));

        return title;
    }

    private void expiredAlarmMessage(String key) {
        Jedis client = RedisUtil.getRedisClient();
        client.expire(key, 0);
        if (client != null) {
            client.close();
        }
    }

    private void savePreviousFireTime(String userId, String ruleId,
                                      long currentFireMinuteTime) {
        Jedis client = RedisUtil.getRedisClient();
        client.hset(userId, ruleId, String.valueOf(currentFireMinuteTime));
        if (client != null) {
            client.close();
        }
    }

    private Collection<String> getAlarmMessages(String key) {
        Jedis client = RedisUtil.getRedisClient();
        Map<String, String> result = client.hgetAll(key);
        if (result == null) {
            return new ArrayList<String>();
        }

        client.close();

        return result.keySet();
    }

    private String generateContent(String templateStr, Map parameter) throws IOException, TemplateException, SQLException {
        Template t = null;
        t = new Template(null, new StringReader(templateStr), TemplateConfigurationUtil.getConfiguration());
        StringWriter out = new StringWriter();
        t.process(parameter, out);
        return out.getBuffer().toString();
    }

    private static Map<String, String> idCodeMapper = new ConcurrentHashMap<String, String>();

    public static String convertAppId2AppCode(String appId) throws SQLException {
        String resultCode = idCodeMapper.get(appId);
        if (resultCode == null) {
            resultCode = AlarmMessageDao.selectAppCodeByAppId(appId);

            idCodeMapper.put(appId, resultCode);
        }

        return idCodeMapper.get(appId);
    }
}
