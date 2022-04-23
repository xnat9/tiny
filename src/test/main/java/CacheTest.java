import cn.xnatural.app.AppContext;
import cn.xnatural.app.CacheSrv;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Objects;

public class CacheTest {


    @Test
    void cacheGet() {
        final AppContext app = new AppContext();
        app.addSource(new CacheSrv());
        app.start();

        CacheSrv cache = app.bean(CacheSrv.class, null);
        cache.set("c1", "aaa");
        Assertions.assertTrue(Objects.equals(cache.get("c1"), "aaa"));
        Assertions.assertTrue(Objects.equals(app.ep().fire("cacheSrv.get", "c1"), "aaa"));
        cache.hset("key", "dataKey", 1);
        Assertions.assertTrue(Objects.equals(cache.hget("key", "dataKey"), 1));
        Assertions.assertTrue(Objects.equals(app.ep().fire("cacheSrv.hget", "key", "dataKey"), 1));
    }


    @Test
    void cacheLimit() {
        int limit = 10;
        final AppContext app = new AppContext();
        app.addSource(new CacheSrv());
        app.env().put("cacheSrv.itemLimit", limit);
        app.start();

        CacheSrv cache = app.bean(CacheSrv.class, null);
        for (int i = 0; i < 200; i++) {
            cache.set("key_" + i, i);
            int v = i;
            cache.hset("key", "" + i, (AutoCloseable) () -> System.out.println("=====h close " + v));
        }
        Assertions.assertTrue(cache.count() <= limit);
    }


    @Test
    void cacheExpire() throws Exception {
        int limit = 10;
        final AppContext app = new AppContext();
        app.addSource(new CacheSrv());
        app.env().put("cacheSrv.itemLimit", limit);
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


        String dataKey = "dataKey";
        cache.hset(key, dataKey, 1, Duration.ofSeconds(5));
        cache.hset(key, dataKey + "1", 1, Duration.ofSeconds(5));
        cache.hset(key, dataKey + "2", 1, Duration.ofSeconds(5));
        Thread.sleep(4000);
        Assertions.assertTrue((Integer) cache.hget(key, dataKey) == 1, "");
        Thread.sleep(1000);
        Assertions.assertTrue(cache.hget(key, dataKey) == null, "");
        Assertions.assertTrue(cache.hget(key, dataKey + "1") == null, "");
    }


    @Test
    void cleanAllExpireRecord() throws Exception {
        int limit = 10;
        final AppContext app = new AppContext();
        app.addSource(new CacheSrv());
        app.env().put("cacheSrv.itemLimit", limit);
        app.start();

        CacheSrv cache = app.bean(CacheSrv.class, null);

        for (int i = 0; i < 4; i++) {
            int v = i;
            cache.set("a" + i, (AutoCloseable) () -> System.out.println("=====close " + v), Duration.ofSeconds(4));
            cache.hset("a" + i, "" + i, (AutoCloseable) () -> System.out.println("=====h close " + v), Duration.ofSeconds(4));
        }
        Thread.sleep(3000L);
        cache.getAndUpdate("a0");
        Thread.sleep(1000L);
        Assertions.assertTrue(cache.count() == 8);
        cache.set("a", "a", Duration.ofSeconds(2)); // 此时会清理所有过期的缓存
        Thread.sleep(3000L);
        Assertions.assertTrue(cache.count() == 2);
    }


    @Test
    void testClose() {
        final AppContext app = new AppContext();
        app.addSource(new CacheSrv());
        app.start();

        String key = "a";

        CacheSrv cache = app.bean(CacheSrv.class, null);
        cache.set(key, (AutoCloseable) () -> System.out.println("=====close"));
        cache.remove(key);

        String dataKey = "a";
        cache.hset(key, dataKey, (AutoCloseable) () -> System.out.println("=====h close"));
        cache.hremove(key, dataKey);
    }
}
