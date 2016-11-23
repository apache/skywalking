import com.a.eye.skywalking.network.ConsumerProvider;

import java.util.concurrent.CountDownLatch;

public class StorageClient {

    private static ConsumerProvider consumerProvider;

    private static       int  THREAD_COUNT = 4;
    private static final long COUNT        = 1_000_000_000;


    public static void main(String[] args) throws InterruptedException {
        consumerProvider = ConsumerProvider.init("10.128.7.241", 34000);
        CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            new StorageThread(consumerProvider, COUNT, countDownLatch).start();
        }

        countDownLatch.await();
    }
}
