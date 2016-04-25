package com.ai.cloud.skywalking.alarm.procesor;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import redis.clients.jedis.Jedis;

import com.ai.cloud.skywalking.alarm.model.AlarmMessage;
import com.ai.cloud.skywalking.alarm.model.AlarmRule;
import com.ai.cloud.skywalking.alarm.model.AlarmType;
import com.ai.cloud.skywalking.alarm.model.ApplicationInfo;
import com.ai.cloud.skywalking.alarm.model.MailInfo;
import com.ai.cloud.skywalking.alarm.model.UserInfo;
import com.ai.cloud.skywalking.alarm.util.AlarmTypeUtil;
import com.ai.cloud.skywalking.alarm.util.MailUtil;
import com.ai.cloud.skywalking.alarm.util.RedisUtil;
import com.ai.cloud.skywalking.alarm.util.RedisUtil.Executable;
import com.ai.cloud.skywalking.alarm.util.TemplateConfigurationUtil;

import freemarker.template.Template;
import freemarker.template.TemplateException;

public class AlarmMessageProcessor {

	private static Logger logger = LogManager
			.getLogger(AlarmMessageProcessor.class);

	private static final String TYPE_OF_EXCEPTION_WARNING = "default";

	static List<AlarmType> alarmTypeList;
	static Template t;

	static {
		alarmTypeList = AlarmTypeUtil.getAlarmTypeList();
	}

	public void process(UserInfo userInfo, AlarmRule rule)
			throws TemplateException, IOException, SQLException {

		Map<String, List<AlarmMessage>> warningMap = new HashMap<String, List<AlarmMessage>>();
		Set<String> warningMessageKeys = new HashSet<String>();
		long currentFireMinuteTime = System.currentTimeMillis() / (1000 * 60);
		long warningTimeWindowSize = currentFireMinuteTime
				- rule.getPreviousFireTimeM();

		// 获取待发送数据
		if (warningTimeWindowSize >= rule.getConfigArgsDescriber().getPeriod()) {
			for (AlarmType alarmType : alarmTypeList) {
				String type = alarmType.getType();
				List<AlarmMessage> warningObjects = new ArrayList<AlarmMessage>();
				for (ApplicationInfo applicationInfo : rule
						.getApplicationInfos()) {
					for (int period = 0; period < warningTimeWindowSize; period++) {
						Long currentMinuteTime = currentFireMinuteTime - period
								- 1;
						String alarmKey = userInfo.getUserId() + "-"
								+ applicationInfo.getAppCode() + "-"
								+ currentMinuteTime;
						if (!TYPE_OF_EXCEPTION_WARNING.equals(type)) {
							alarmKey += "-" + type;
						}
						warningMessageKeys.add(alarmKey);
						setAlarmMessages(alarmKey, warningObjects);
					}
				}
				if (warningObjects.size() > 0) {
					warningMap.put(type, warningObjects);
				}
			}

			// 发送告警数据
			int warningSize = this.getWarningSize(warningMap);
			int exceptionAlarmSize = this.getExceptionSize(warningMap);
			if (warningSize > 0) {
				if ("0".equals(rule.getTodoType())) {
					logger.info(
							"A total of {} alarm information needs to be sent {}",
							warningSize, rule.getConfigArgsDescriber()
									.getMailInfo().getMailTo());
					// 发送邮件
					String subjects = generateSubject(userInfo.getUserName(),
							exceptionAlarmSize, warningSize,
							rule.getPreviousFireTimeM(), currentFireMinuteTime);
					Map<String, Object> parameter = new HashMap<String, Object>();

					parameter.put("alarmTypeList", alarmTypeList);
					parameter.put("warningMap", warningMap);
					parameter.put("name", userInfo.getUserName());
					parameter.put("startDate", new SimpleDateFormat(
							"yyyy-MM-dd HH:mm:ss").format(new Date(rule
							.getPreviousFireTimeM() * 10000 * 6)));
					parameter.put("endDate", new SimpleDateFormat(
							"yyyy-MM-dd HH:mm:ss").format(new Date(
							currentFireMinuteTime * 10000 * 6)));

					String mailContext = generateContent(parameter);
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

	private String generateSubject(String userName, int exceptionAlarmSize,
			int count, long startTime, long endTime) {
		String title = (exceptionAlarmSize > 0 ? "[Error]" : "[Warning]")
				+ " Dear  "
				+ userName
				+ ", SkyWalking collects "
				+ (exceptionAlarmSize > 0 ? exceptionAlarmSize
						+ " tid of system exceptions, " : "");
		if (count > exceptionAlarmSize) {
			title += (count - exceptionAlarmSize) + " tid of warings, ";
		}
		title += "between "
				+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(
						startTime * 10000 * 6))
				+ " to "
				+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(
						endTime * 10000 * 6));

		return title;
	}

	private void expiredAlarmMessage(final String key) {
		RedisUtil.execute(new Executable<Long>() {
			@Override
			public Long exe(Jedis client) {
				return client.expire(key, 0);
			}
		});
	}

	private void savePreviousFireTime(final String userId, final String ruleId,
			final long currentFireMinuteTime) {
		RedisUtil.execute(new Executable<Long>() {
			@Override
			public Long exe(Jedis client) {
				return client.hset(userId, ruleId,
						String.valueOf(currentFireMinuteTime));
			}
		});
	}

	private void setAlarmMessages(final String key,
			final Collection<AlarmMessage> warningTracingIds) {
		RedisUtil.execute(new Executable<Object>() {
			@Override
			public Collection<String> exe(Jedis client) {
				Map<String, String> result = client.hgetAll(key);
				if (result != null) {
					for (String traceid : result.keySet()) {
						warningTracingIds.add(new AlarmMessage(traceid, result
								.get(traceid)));
					}
				}
				return null;
			}
		});
	}

	private String generateContent(Map parameter) throws IOException,
			TemplateException, SQLException {

		if (t == null) {
			t = TemplateConfigurationUtil.getConfiguration().getTemplate(
					"mail-template.ftl");
		}
		StringWriter out = new StringWriter();
		t.process(parameter, out);
		return out.getBuffer().toString();
	}

	private int getExceptionSize(Map<String, List<AlarmMessage>> warningMap) {
		if (warningMap.containsKey(TYPE_OF_EXCEPTION_WARNING)) {
			return warningMap.get(TYPE_OF_EXCEPTION_WARNING).size();
		}
		return 0;
	}

	private int getWarningSize(Map<String, List<AlarmMessage>> warningMap) {
		int result = 0;
		for (Entry<String, List<AlarmMessage>> entry : warningMap.entrySet()) {
			if (entry.getValue() != null) {
				result += entry.getValue().size();
			}
		}
		return result;
	}
}
