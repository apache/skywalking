import java.util.concurrent.CountDownLatch;

public class StorageClient {

    private static       int  THREAD_COUNT = 1;
    private static final long COUNT        = 1;


    public static void main(String[] args) throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            new StorageThread(COUNT, countDownLatch, i).start();
        }

        countDownLatch.await();
    }
}
