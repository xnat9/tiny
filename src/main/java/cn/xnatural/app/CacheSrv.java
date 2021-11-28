package cn.xnatural.app;

import cn.xnatural.enet.event.EL;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单内存缓存服务
 */
public class CacheSrv extends ServerTpl {
    protected final Lazier<Integer> _limit = new Lazier(() -> getInteger("itemLimit", 1000));

    /**
     * 数据存放
     */
    protected final Lazier<Map<String, Record>> _data = new Lazier(() -> new ConcurrentHashMap( _limit.get() / 2));


    public CacheSrv(String name) { super(name); }

    public CacheSrv() {}


    /**
     * 设置缓存
     * 取默认过期时间 30 分钟
     * @param key 缓存key
     * @param value 缓存值
     */
    public CacheSrv set(String key, Object value) {
        return set(key, value, Duration.ofMinutes(getInteger("defaultExpire", getInteger("expire." + key, 30))));
    }


    /**
     * 设置缓存
     * @param key 缓存key
     * @param value 缓存值
     * @param expire 过期时间
     */
    @EL(name = "{name}.set")
    public CacheSrv set(String key, Object value, Duration expire) {
        log.trace("Set cache. key: {}, value: {}, expire: {}", key, value, expire);
        _data.get().put(key, new Record(expire == null ? null : expire.toMillis(), value));
        boolean onlyCleanExpired = _data.get().size() < _limit.get(); // 是否只清理已过期的数据
        Runnable clean = () -> {
            Map.Entry<String, Record> oldEntry = null; // 最老的缓存
            long oldLeft = 0;
            for (Iterator<Map.Entry<String, Record>> iter = _data.get().entrySet().iterator(); iter.hasNext(); ) { //遍历选出最应该被移出的缓存记录
                Map.Entry<String, Record> entry = iter.next();
                long left = entry.getValue().left();
                if (left < 1) { // 当前缓存记录已经失效了,删除
                    iter.remove(); oldEntry = null; break;
                }
                if (onlyCleanExpired) continue;
                // 优先清理过期时间越近的
                if (
                        oldEntry == null ||
                        left < oldLeft ||
                        (oldLeft == left && oldEntry.getValue().updateTime < entry.getValue().updateTime)
                ) {
                    oldEntry = entry;
                    oldLeft = left;
                }
            }
            if (oldEntry != null) {
                _data.get().remove(oldEntry.getKey());
            }
        };
        if (onlyCleanExpired) async(clean); // 异步清除多余的缓存
        else clean.run(); // 同步清理: 避免异步排对太多而不能及时清理造成内存占用过多而溢出
        return this;
    }


    /**
     * 重新更新过期时间
     * @param key 缓存key
     * @param expire 过期时间
     * @return 缓存值
     */
    @EL(name = "{name}.expire")
    public Object expire(String key, Duration expire) {
        Record record = _data.get().get(key);
        if (record != null) {
            if (expire != null) {
                record.expire = expire.toMillis();
                record.updateTime = System.currentTimeMillis();
                log.debug("Updated cache: {}, expire: {}", key, expire);
            } else {
                _data.get().remove(key);
                log.debug("Removed cache: {}", key);
            }
            return record.value;
        }
        return null;
    }


    /**
     * 移除缓存
     * @param key 缓存key
     * @return 缓存值
     */
    @EL(name = "{name}.remove")
    public Object remove(String key) { return expire(key, null); }


    /**
     * 获取缓存值
     * @param key 缓存key
     * @return 缓存值
     */
    @EL(name = "{name}.get")
    public Object get(String key) {
        Record record = _data.get().get(key);
        if (record == null) return null;
        if (record.valid()) return record.value;
        _data.get().remove(key);
        return null;
    }


    @Override
    public String toString() {
        return "CacheSrv@" + Integer.toHexString(hashCode()) + "[size=" + _data.get().size() + ", limit=" + _limit.get() + "]";
    }


    /**
     * 缓存记录
     */
    protected class Record {
        // null 或者 < 1, 都是永不过期
        Long     expire;
        // 更新时间
        Long     updateTime = System.currentTimeMillis();
        // 缓存值
        Object   value;

        public Record(Long expire, Object value) {
            this.expire = expire;
            this.value = value;
        }

        /**
         * 是否有效
         */
        boolean valid() { return left() > 0; }

        /**
         * 剩多长时间才失效
         */
        long left() { return (expire == null || expire < 1) ? Long.MAX_VALUE : ((updateTime + expire) - System.currentTimeMillis()); }
    }
}
