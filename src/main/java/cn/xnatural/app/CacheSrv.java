package cn.xnatural.app;

import cn.xnatural.enet.event.EL;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 简单内存缓存服务
 */
public class CacheSrv extends ServerTpl {
    // 触发清理多余数据的条件
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
        _data.get().put(key, new Record(record -> expire == null ? Long.MAX_VALUE : expire.toMillis() + record.updateTime, value));
        clean();
        return this;
    }


    /**
     * 设置缓存
     * @param key 缓存key
     * @param value 缓存值
     * @param expireFn 过期时间计算函数
     *                 函数返回过期时间点(时间缀), 返回null(不过期,除非达到缓存限制被删除)
     */
    @EL(name = "{name}.set2")
    public CacheSrv set(String key, Object value, Function<Record, Long> expireFn) {
        log.trace("Set cache. key: {}, value: {}", key, value);
        _data.get().put(key, new Record(expireFn, value));
        clean();
        return this;
    }


    /**
     * 移除缓存
     * @param key 缓存key
     * @return 缓存值
     */
    @EL(name = "{name}.remove")
    public Object remove(String key) {
        Record record = _data.get().remove(key);
        if (record == null) return null;
        return record.value;
    }


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


    /**
     * 获取缓存值, 并更新缓存时间(即从现在开始重新计算过期时间)
     * @param key 缓存key
     * @return 缓存值
     */
    @EL(name = "{name}.getAndUpdate")
    public Object getAndUpdate(String key) {
        Record record = _data.get().get(key);
        if (record == null) return null;
        if (record.valid()) {
            record.updateTime = System.currentTimeMillis();
            return record.value;
        }
        _data.get().remove(key);
        return null;
    }


    /**
     * 清理过期和多余的数据
     * 一次最多清一条
     * 1. 如果没有达到缓存限制, 则只清理过期数据
     * 2. 优先清理过期时间越近的
     * 3. 过期时间一样则先清更新时间最早的
     */
    protected void clean() {
        boolean onlyCleanExpired = _data.get().size() < _limit.get(); // 是否只清理已过期的数据
        final Runnable clean = () -> {
            Map.Entry<String, Record> oldEntry = null; // 最老的缓存
            long oldLeft = 0;
            long now = System.currentTimeMillis();
            for (Iterator<Map.Entry<String, Record>> iter = _data.get().entrySet().iterator(); iter.hasNext(); ) { //遍历选出最应该被移出的缓存记录
                Map.Entry<String, Record> entry = iter.next();
                long left = entry.getValue().left(now);
                if (left < 1) { // 过期数据
                    iter.remove(); oldEntry = null; break;
                }
                if (onlyCleanExpired) continue;

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
        if (onlyCleanExpired) queue(clean); // 异步清除多余的缓存
        else clean.run(); // 同步清理: 避免异步排对太多而不能及时清理造成内存占用过多而溢出
    }


    @Override
    public String toString() {
        return "CacheSrv@" + Integer.toHexString(hashCode()) + "[size=" + _data.get().size() + ", limit=" + _limit.get() + "]";
    }


    /**
     * 缓存记录
     */
    public class Record {
        // 过期时间点计算函数, 小于当前时间即过期, 返回null不过期
        protected final Function<Record, Long> expireFn;
        // 更新时间
        protected long updateTime = System.currentTimeMillis();
        // 缓存的对象
        public final Object value;

        protected Record(Function<Record, Long> expireFn, Object value) {
            this.expireFn = expireFn;
            this.value = value;
        }

        /**
         * 是否有效
         */
        protected boolean valid() { return left(System.currentTimeMillis()) > 0; }

        /**
         * 相对于某个时间, 还剩多长时间才失效
         */
        protected long left(long timePoint) {
            Long expireTime = expireFn == null ? null : expireFn.apply(this);
            return expireTime == null ? Long.MAX_VALUE : timePoint - expireTime;
        }


        public long getUpdateTime() { return updateTime; }
    }
}
