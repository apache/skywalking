package com.ai.cloud.skywalking.reciever.storage.chain;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.storage.Chain;
import com.ai.cloud.skywalking.reciever.storage.IStorageChain;
import com.ai.cloud.skywalking.reciever.storage.chain.alarm.ExceptionChecker;
import com.ai.cloud.skywalking.reciever.storage.chain.alarm.ExecuteTimeChecker;
import com.ai.cloud.skywalking.reciever.storage.chain.alarm.ISpanChecker;

import static com.ai.cloud.skywalking.reciever.conf.Config.Alarm.Checker.*;

public class AlarmChain implements IStorageChain {
	private static Logger logger = LogManager.getLogger(AlarmChain.class);

	private List<ISpanChecker> checkList = new ArrayList<ISpanChecker>();

	public AlarmChain() {
		if (TURN_ON_EXCEPTION_CHECKER)
			checkList.add(new ExceptionChecker());
		if (TURN_ON_EXECUTE_TIME_CHECKER)
			checkList.add(new ExecuteTimeChecker());
	}

	@Override
	public void doChain(List<Span> spans, Chain chain) {
		if (Config.Alarm.ALARM_OFF_FLAG) {
			return;
		}

		for (Span span : spans) {
			for (ISpanChecker checker : checkList) {
				checker.check(span);
			}
		}
		chain.doChain(spans);
	}

}
