package com.ai.cloud.skywalking.alarm.procesor;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import redis.clients.jedis.Jedis;

import com.ai.cloud.skywalking.alarm.model.AlarmRule;
import com.ai.cloud.skywalking.alarm.model.ApplicationInfo;
import com.ai.cloud.skywalking.alarm.model.MailInfo;
import com.ai.cloud.skywalking.alarm.model.UserInfo;
import com.ai.cloud.skywalking.alarm.util.MailUtil;
import com.ai.cloud.skywalking.alarm.util.RedisUtil;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;

public class AlarmMessageProcessor {

	private static Logger logger = LogManager
			.getLogger(AlarmMessageProcessor.class);

	public void process(UserInfo userInfo, AlarmRule rule) {
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
							+ ((System.currentTimeMillis() / (10000 * 6)) - period);

					warningMessageKeys.add(alarmKey);
					warningTracingIds.addAll(getAlarmMessages(alarmKey));
				}
			}

			// 发送告警数据
			if (warningTracingIds.size() > 0) {
				if ("0".equals(rule.getTodoType())) {
					// 发送邮件
					String subjects = generateSubject(warningTracingIds.size(),
							rule.getPreviousFireTimeM(), currentFireMinuteTime);
					Map parameter = new HashMap();
					// TODO：已使用新的参数，warningTracingIds包含所有的告警tracingId，需要在模板中生成链接
					parameter.put("warningTracingIds", warningTracingIds);
					// TODO：请转换为USERNAME，ID无法识别
					parameter.put("name", userInfo.getUserId());
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
						startTime))
				+ " to "
				+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(
						endTime));

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

		return result.values();
	}

	private String generateContent(String templateStr, Map parameter) {
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
