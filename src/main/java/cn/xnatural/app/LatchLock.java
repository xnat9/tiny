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
     * 获取一个锁
     * <pre>
     * final LatchLock lock = new LatchLock();
     * lock.limit(3); // 设置并发限制. 默认为1
     * if (lock.tryLock()) { // 尝试获取一个锁
     *     try {
     *         // 被执行的代码块
     *     } finally {
     *         lock.release(); // 释放一个锁
     *     }
     * }
     * </pre>
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
     * 一般是 {@link #tryLock()} 成功后 调一次
     * @return 释放后当前锁的个数
     */
    public int release() {
        int latch = latchSize.get();
        if (latch <= 0) return 0;
        if (latchSize.compareAndSet(latch, latch - 1)) {
            return latch - 1;
        }
        return latchSize.get();
    }


    /**
     * 当前已获取锁个数
     */
    public int getLatchSize() { return latchSize.get(); }
}
