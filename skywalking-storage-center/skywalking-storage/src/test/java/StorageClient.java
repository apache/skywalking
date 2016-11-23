import java.util.concurrent.CountDownLatch;

public class StorageClient {

    private static       int  THREAD_COUNT = 4;
    private static final long COUNT        = 1_000_000_000;


    public static void main(String[] args) throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            new StorageThread(COUNT, countDownLatch).start();
        }

        countDownLatch.await();
    }
}
