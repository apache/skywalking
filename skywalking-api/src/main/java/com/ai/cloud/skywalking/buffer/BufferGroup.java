package com.ai.cloud.skywalking.buffer;

import static com.ai.cloud.skywalking.conf.Config.Buffer.BUFFER_MAX_SIZE;
import static com.ai.cloud.skywalking.conf.Config.Consumer.CONSUMER_FAIL_RETRY_WAIT_INTERVAL;
import static com.ai.cloud.skywalking.conf.Config.Consumer.MAX_CONSUMER;
import static com.ai.cloud.skywalking.conf.Config.Consumer.MAX_WAIT_TIME;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.conf.Constants;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.sender.DataSenderFactoryWithBalance;
import com.ai.cloud.skywalking.util.AtomicRangeInteger;

public class BufferGroup {
	private static Logger logger = LogManager.getLogger(BufferGroup.class);
	private String groupName;
	private Span[] dataBuffer = new Span[BUFFER_MAX_SIZE];
	AtomicRangeInteger index = new AtomicRangeInteger(0, BUFFER_MAX_SIZE);

	public BufferGroup(String groupName) {
		this.groupName = groupName;

		int step = (int) Math.ceil(BUFFER_MAX_SIZE * 1.0 / MAX_CONSUMER);
		int start = 0, end = 0;
		while (true) {
			if (end + step >= BUFFER_MAX_SIZE) {
				new ConsumerWorker(start, BUFFER_MAX_SIZE).start();
				break;
			}
			end += step;
			new ConsumerWorker(start, end).start();
			start = end;
		}
	}

	public void save(Span span) {
		int i = index.getAndIncrement();
		if (dataBuffer[i] != null) {
			logger.warn(
					"Group[{}] index[{}] data collision, discard old data.",
					groupName, i);
		}
		dataBuffer[i] = span;
	}

	class ConsumerWorker extends Thread {
		private int start = 0;
		private int end = BUFFER_MAX_SIZE;

		private ConsumerWorker(int start, int end) {
			super("ConsumerWorker");
			this.start = start;
			this.end = end;
		}

		@Override
		public void run() {
			StringBuilder data = new StringBuilder();
			while (true) {
				boolean bool = false;
				try {
					for (int i = start; i < end; i++) {
						if (dataBuffer[i] == null) {
							continue;
						}
						bool = true;
						if (data.length() + dataBuffer[i].toString().length() >= Config.Sender.MAX_SEND_LENGTH) {
							while (!DataSenderFactoryWithBalance.getSender()
									.send(data.toString())) {
								try {
									Thread.sleep(CONSUMER_FAIL_RETRY_WAIT_INTERVAL);
								} catch (InterruptedException e) {
									logger.error("Sleep Failure");
								}
							}
							logger.debug("send buried-point data, size:{}", data.length());
							data = new StringBuilder();
						}

						data.append(dataBuffer[i] + Constants.DATA_SPILT);
						dataBuffer[i] = null;
					}

					if (data != null && data.length() > 0) {
						while (!DataSenderFactoryWithBalance.getSender().send(
								data.toString())) {
							try {
								Thread.sleep(CONSUMER_FAIL_RETRY_WAIT_INTERVAL);
							} catch (InterruptedException e) {
								logger.error("Sleep Failure");
							}
						}
						data = new StringBuilder();
					}
				} catch (Throwable e) {
					logger.error("buffer group running failed", e);
				}

				if (!bool) {
					try {
						Thread.sleep(MAX_WAIT_TIME);
					} catch (InterruptedException e) {
						logger.error("Sleep Failure");
					}
				}
			}
		}
	}

	public String getGroupName() {
		return groupName;
	}

}
