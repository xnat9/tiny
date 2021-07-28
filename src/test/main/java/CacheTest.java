import cn.xnatural.app.AppContext;
import cn.xnatural.app.CacheSrv;
import org.junit.jupiter.api.Test;

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
}
