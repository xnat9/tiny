import cn.xnatural.app.AppContext;
import cn.xnatural.app.CacheSrv;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

public class CacheTest {


    @Test
    void cacheGet() {
        final AppContext app = new AppContext();
        app.addSource(new CacheSrv());
        app.start();

        CacheSrv cache = app.bean(CacheSrv.class, null);
        cache.set("c1", "aaa");
        System.out.println(cache.get("c1"));
        System.out.println(app.ep().fire("cacheSrv.get", "c1"));
    }


    @Test
    void cacheLimit() {
        final AppContext app = new AppContext();
        app.addSource(new CacheSrv());
        app.start();

        CacheSrv cache = app.bean(CacheSrv.class, null);
        for (int i = 0; i < 200; i++) {
            cache.set("key_" + i, i);
        }
        System.out.println(cache);
    }


    @Test
    void cacheExpire() throws Exception {
        final AppContext app = new AppContext();
        app.addSource(new CacheSrv());
        app.start();

        String key = "a";

        CacheSrv cache = app.bean(CacheSrv.class, null);
        cache.set(key, 1, Duration.ofSeconds(5));
        Thread.sleep(4000);
        Assertions.assertTrue((Integer) cache.get(key) == 1, "");
        Thread.sleep(1000);
        Assertions.assertTrue(cache.get(key) == null, "");

        long now = System.currentTimeMillis();
        cache.set(key, 1, r -> 5000 + now);
        Thread.sleep(4000);
        Assertions.assertTrue((Integer) cache.get(key) == 1, "");
        Thread.sleep(1000);
        Assertions.assertTrue(cache.get(key) == null, "");
    }


    @Test
    void testClose() {
        final AppContext app = new AppContext();
        app.addSource(new CacheSrv());
        app.start();

        String key = "a";

        CacheSrv cache = app.bean(CacheSrv.class, null);
        cache.set(key, new AutoCloseable() {
            @Override
            public void close() throws Exception {
                System.out.println("=====close");
            }
        });
        cache.remove(key);
    }
}
