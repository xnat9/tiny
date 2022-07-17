package cn.xnatural.app;

import cn.xnatural.enet.event.EL;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 简单内存缓存服务
 */
public class CacheSrv extends ServerTpl {
    // 触发清理多余数据的条件
    protected final Lazier<Integer> _limit = new Lazier(() -> {
        queue(name).useLast(true);
        return getInteger("itemLimit", 1000);
    });

    /**
     * 数据存放
     */
    protected final Lazier<Map<String, Record>> _data = new Lazier(() -> new ConcurrentHashMap( _limit.get() / 3));
    /**
     * hash 数据缓存
     */
    protected final Lazier<Map<String, Map<String, Record>>> _hashdata = new Lazier(() -> new ConcurrentHashMap( _limit.get() / 3));


    public CacheSrv(String name) { super(name); }

    public CacheSrv() {}


    /**
     * 设置缓存
     * 取默认过期时间 30 分钟
     * @param key 缓存key
     * @param value 缓存值
     * @return 以前的值
     */
    public Object set(String key, Object value) {
        return set(key, value, Duration.ofMinutes(getInteger("defaultExpire", getInteger("expire." + key, 30))));
    }


    /**
     * 设置缓存
     * 取默认过期时间 30 分钟
     * @param key 缓存key
     * @param dataKey 数据key
     * @param value 缓存值
     * @return 以前的值
     */
    public Object hset(String key, String dataKey, Object value) {
        return hset(key, dataKey, value, Duration.ofMinutes(getInteger("defaultExpire", getInteger("expire." + key, 30))));
    }


    /**
     * 设置缓存
     * @param key 缓存key
     * @param value 缓存值
     * @param expire 过期时间
     * @return 以前的值
     */
    @EL(name = "{name}.set")
    public Object set(String key, Object value, Duration expire) {
        return set(key, value, record -> expire == null ? Long.MAX_VALUE : expire.toMillis() + record.updateTime);
    }


    /**
     * 设置缓存
     * @param key 缓存key
     * @param dataKey 数据key
     * @param value 缓存值
     * @param expire 过期时间
     * @return 以前的值
     */
    @EL(name = "{name}.hset")
    public Object hset(String key, String dataKey, Object value, Duration expire) {
        return hset(key, dataKey, value, record -> expire == null ? Long.MAX_VALUE : expire.toMillis() + record.updateTime);
    }


    /**
     * 设置缓存
     * @param key 缓存key
     * @param value 缓存值
     * @param expireFn 过期时间计算函数
     *                 函数返回过期时间点(时间缀), 返回null(不过期,除非达到缓存限制被删除)
     * @return 以前的值
     */
    @EL(name = "{name}.set2")
    public Object set(String key, Object value, Function<Record, Long> expireFn) {
        log.trace("Set cache. key: {}, value: {}", key, value);
        Record old = _data.get().put(key, new Record(expireFn, value));
        if (old != null) old.close(key);
        clean();
        return old == null ? null : old.value;
    }


    /**
     * 设置缓存
     * @param key 缓存key
     * @param dataKey 数据key
     * @param value 缓存值
     * @param expireFn 过期时间计算函数
     *                 函数返回过期时间点(时间缀), 返回null(不过期,除非达到缓存限制被删除)
     * @return 以前的值
     */
    @EL(name = "{name}.hset2")
    public Object hset(String key, String dataKey, Object value, Function<Record, Long> expireFn) {
        log.trace("HSet cache. key: {}, dataKey: {}, value: {}", key, dataKey, value);
        Map<String, Record> data = _hashdata.get().get(key);
        if (data == null) {
            synchronized (_hashdata) {
                data = _hashdata.get().get(key);
                if (data == null) {
                    data = new ConcurrentHashMap<>();
                    _hashdata.get().put(key, data);
                }
            }
        }
        Record old = data.put(dataKey, new Record(expireFn, value));
        if (old != null) old.close(dataKey);
        clean();
        return old == null ? null : old.value;
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
        record.close(key);
        return record.value;
    }


    /**
     * 移除缓存
     * @param key 缓存key
     * @param dataKey 数据key
     * @return 缓存值
     */
    @EL(name = "{name}.hremove")
    public Object hremove(String key, String dataKey) {
        Map<String, Record> data = _hashdata.get().get(key);
        if (data == null) return null;
        Record record = data.remove(dataKey);
        if (record == null) return null;
        record.close(dataKey);
        if (data.isEmpty()) {
            synchronized (_hashdata) {
                if (data.isEmpty()) {
                    _hashdata.get().remove(key);
                }
            }
        }
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
        remove(key);
        return null;
    }


    /**
     * 获取缓存值
     * @param key 缓存key
     * @param dataKey 数据key
     * @return 缓存值
     */
    @EL(name = "{name}.hget")
    public Object hget(String key, String dataKey) {
        Map<String, Record> data = _hashdata.get().get(key);
        if (data == null) return null;
        Record record = data.get(dataKey);
        if (record == null) return null;
        if (record.valid()) return record.value;
        hremove(key, dataKey);
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
        remove(key);
        return null;
    }


    /**
     * 获取缓存值, 并更新缓存时间(即从现在开始重新计算过期时间)
     * @param key 缓存key
     * @param dataKey 数据key
     * @return 缓存值
     */
    @EL(name = "{name}.hgetAndUpdate")
    public Object hgetAndUpdate(String key, String dataKey) {
        Map<String, Record> data = _hashdata.get().get(key);
        if (data == null) return null;
        Record record = data.get(dataKey);
        if (record == null) return null;
        if (record.valid()) {
            record.updateTime = System.currentTimeMillis();
            return record.value;
        }
        hremove(key, dataKey);
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
        // 是否只清理已过期的数据
        final boolean onlyCleanExpired = count() < _limit.get();
        final long now = System.currentTimeMillis();
        final Runnable clean = () -> {
            AtomicInteger cleanCnt = new AtomicInteger(); //清理的过期缓存条数统计
            AtomicReference<Map<String, Record>> oldestData = new AtomicReference<>();
            AtomicReference<String> oldestKey = new AtomicReference<>();
            AtomicReference<Record> oldestRecord = new AtomicReference<>();
            final Consumer<Map<String, Record>> doClean = data -> {
                long oldLeft = 0;
                for (Iterator<Map.Entry<String, Record>> iter = data.entrySet().iterator(); iter.hasNext(); ) { //遍历选出最应该被移出的缓存记录
                    Map.Entry<String, Record> entry = iter.next();
                    long left = entry.getValue().left(now);
                    if (left < 1) { // 删除所有过期数据
                        iter.remove();
                        entry.getValue().close(entry.getKey());
                        oldestData.set(null);
                        cleanCnt.incrementAndGet();
                        if (!onlyCleanExpired) break; // 如果是同步删除, 为了减少遍历时间, 则只要有一个删除就退出遍历
                    }
                    if (onlyCleanExpired || cleanCnt.get() > 0) continue;

                    if (
                            oldestRecord.get() == null ||
                            left < oldLeft ||
                            (oldLeft == left && oldestRecord.get().updateTime < entry.getValue().updateTime)
                    ) {
                        oldestData.set(data);
                        oldestKey.set(entry.getKey());
                        oldestRecord.set(entry.getValue());
                        oldLeft = left;
                    }
                }
            };
            doClean.accept(_data.get());
            for (Map<String, Record> data : _hashdata.get().values()) {
                if (cleanCnt.get() > 0 && !onlyCleanExpired) break; // 如果是同步删除, 为了减少遍历时间, 则只要有一个删除就退出遍历
                doClean.accept(data);
            }
            // 同步删除, 必须删除一个最老的缓存
            if (!onlyCleanExpired && cleanCnt.get() < 1 && oldestData.get() != null) {
                // 有可能性: 在删除的时候此条记录已被重新更新了 暂时先不管
                Record removed = oldestData.get().remove(oldestKey.get());
                if (removed != null) removed.close(oldestKey.get());
            }
        };
        if (onlyCleanExpired) queue(clean); // 异步清除多余的缓存
        else clean.run(); // 同步清理: 避免异步排对太多而不能及时清理造成内存占用过多而溢出
    }


    /**
     * 当前缓存的个数统计
     */
    @EL(name = "{name}.count")
    public int count() { return _data.get().size() + _hashdata.get().values().stream().mapToInt(Map::size).sum(); }


    @Override
    public String toString() {
        return "CacheSrv@" + Integer.toHexString(hashCode()) + "[size=" + count() + ", limit=" + _limit.get() + "]";
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
            return expireTime == null ? Long.MAX_VALUE : expireTime - timePoint;
        }


        public long getUpdateTime() { return updateTime; }


        /**
         * 如果缓存值是 AutoCloseable,则在失效时 执行 close
         * @param key 缓存key
         */
        protected void close(String key) {
            if (value instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) value).close();
                } catch (Exception e) {
                    log.error("Remove cache: " +key+ " close error", e);
                }
            }
        }
    }
}
