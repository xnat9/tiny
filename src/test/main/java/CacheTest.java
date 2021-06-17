import cn.xnatural.app.AppContext;
import cn.xnatural.app.CacheSrv;

public class CacheTest {

    public static void main(String[] args) {
        final AppContext app = new AppContext();
        app.addSource(new CacheSrv());
        app.start();

        CacheSrv cache = app.bean(CacheSrv.class, null);
        cache.set("c1", "aaa");
        System.out.println(cache.get("c1"));
    }
}
