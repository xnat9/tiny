import cn.xnatural.app.Devourer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DevourerTest {
    static final Logger log = LoggerFactory.getLogger(DevourerTest.class);

    @Test
    void testParallel() throws Exception {
        Devourer devourer = new Devourer("parallel");
        devourer.parallel(3);
        long start = System.currentTimeMillis();
        AtomicBoolean stop = new AtomicBoolean(false);
        while (System.currentTimeMillis() - start < 1000 * 15 && !stop.get()) {
            devourer.offer(() -> {
                int p = devourer.getParallel();
                if (p > 3) {
                    log.info("========================" + p);
                    stop.set(true);
                }
                log.info("wait " + devourer.getWaitingCount() + ", " + p);
//                Utils.http().get("http://xnatural.cn:9090/test/cus?p1=" + count.getAndIncrement()).debug().execute();
//                try {
//                    Thread.sleep(2000 + new Random().nextInt(10000));
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
            });
            // if (devourer.getWaitingCount() > 40) Thread.sleep(100 + new Random().nextInt(10000));
        }
        Thread.sleep(1000 * 60 * 30);
    }


    @Test
    void testParallel2() throws Exception {
        Devourer devourer = new Devourer("parallel2");
        devourer.parallel(3);
        for (int i = 0; i < 10; i++) {
            int j = i + 1;
            devourer.offer(() -> {
                log.info("start-" + j);
                try {
                    Thread.sleep(1000 * 5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                log.info("end-" + j);
            });
        }
        Thread.sleep(1000 * 60 * 3);
    }


    @Test
    void testSuspend() throws Exception {
        Devourer devourer = new Devourer("suspend");
        devourer.offer(() -> {
            log.info("执行");
            devourer.suspend(Duration.ofMillis(1500));
        });

        for (int i = 0; i < 50; i++) {
            int finalI = i;
            Thread.sleep(100);
            devourer.offer(() -> {
                log.info("执行 " + finalI);
            });
            log.info("added" + i);
        }
        devourer.resume();
        Thread.sleep(60 * 1000);
    }


    @Test
    void testUseLast() throws Exception {
        Devourer devourer = new Devourer("useLast").useLast(true);
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            int finalI = i;
            devourer.offer(() -> {
                log.info("==========" + finalI);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log.error("", e);
                }
            });
        }
        Thread.sleep(20 * 1000);
    }


    @Test
    void testSpeed() throws Exception {
        Devourer devourer = new Devourer();
        devourer.speed("8/s");

        ExecutorService exec = Executors.newFixedThreadPool(2, new ThreadFactory() {
            final AtomicInteger i = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,"t-" + i.incrementAndGet());
            }
        });

        final AtomicBoolean stop = new AtomicBoolean(false);

        exec.execute(() -> {
            while (!stop.get()) {
                devourer.offer(() -> {
                    log.info("=====left: " + devourer.getWaitingCount());
                });
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    log.error("offer error", e);
                }
            }
        });


        Thread.sleep(1000 * 60);
        stop.set(true);
        Thread.sleep(1000 * 20);
        exec.shutdown();
    }
}
