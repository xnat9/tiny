import cn.xnatural.app.LatchLock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LatchLockTest {


    @Test
    void testRelease() throws Exception {
        LatchLock lock = new LatchLock();

        ExecutorService exec = Executors.newFixedThreadPool(5, new ThreadFactory() {
            final AtomicInteger i = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,"t-" + i.incrementAndGet());
            }
        });

        final AtomicBoolean stop = new AtomicBoolean(false);

        exec.execute(() -> {
            while (!stop.get()) {
                lock.tryLock();
            }
        });
        exec.execute(() -> {
            while (!stop.get()) {
                int i = lock.release();
                if (i < 0) {
                    System.out.println("error: after release lock < 0");
                }
            }
        });
        exec.execute(() -> {
            while (!stop.get()) {
                int i = lock.release();
                if (i < 0) {
                    System.out.println("error: after release lock < 0");
                }
            }
        });


        Thread.sleep(1000 * 60 * 2);
        stop.set(true);
        exec.shutdown();
    }
}
