package cn.xnatural.app;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流量限制锁
 */
public class LatchLock {
    /**
     * 流量限制大小
     */
    protected int limit = 1;
    /**
     * 当前并发大小
     */
    protected final AtomicInteger latchSize = new AtomicInteger();


    /**
     * 设置限制
     * @param limit >0
     */
    public LatchLock limit(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Param limit must >0");
        this.limit = limit;
        return this;
    }


    /**
     * 获取锁
     */
    public boolean tryLock() {
        int latch = latchSize.get();
        if (latch >= limit) return false;
        if (!latchSize.compareAndSet(latch, latch + 1)) {
            if (latchSize.get() < limit) return tryLock();
            return false;
        }
        return true;
    }


    /**
     * 释放一个锁
     * @return 释放后当前锁的个数
     */
    public int release() { return latchSize.decrementAndGet(); }


    /**
     * 当前已获取锁个数
     */
    public int getLatchSize() { return latchSize.get(); }
}
