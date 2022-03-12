package cn.xnatural.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 对列执行器
 * 按顺序执行添加的Runnable
 * 当需要按顺序控制任务 一个一个, 两个两个... 的执行任务时
 */
public class Devourer {
    protected static final Logger log = LoggerFactory.getLogger(Devourer.class);
    /**
     * 线程池
     */
    protected final Executor exec;
    /**
     * 队列标识
     */
    protected final String key;
    /**
     * 对列中失败的最大保留个数,
     * 如果 大于0 执行失败, 暂时保留, 直至 排对的个数大于此值
     * 否则 失败丢弃
     * 默认: 执行失败,丢弃
     */
    protected Integer failMaxKeep = 0;
    /**
     * 流量限制锁
     */
    protected final LatchLock lock = new LatchLock();
    /**
     * 任务执行对列
     */
    protected final Deque<Runnable> waiting = new ConcurrentLinkedDeque<>();
    /**
     * 错误处理函数
     */
    protected BiConsumer<Throwable, Devourer> errorHandler;
    /**
     * 暂停执行条件
     */
    protected Predicate<Devourer> pauseCondition;
    /**
     * 使用最后入队想任务/任务最后有效 {@link #useLast(boolean)}
     */
    protected boolean useLast;
    /**
     * 速度限制
     * 平均每个占用执行时间
     */
    protected Long perSpend;


    /**
     * 创建对列
     * @param key 对列标识
     * @param exec 线程池
     */
    public Devourer(String key, Executor exec) {
        this.key = (key == null || key.isEmpty()) ? Devourer.class.getSimpleName() + "@" + Integer.toHexString(hashCode()) : key;
        this.exec = exec == null ? new ThreadPoolExecutor(2, 4, 6L, TimeUnit.HOURS,
                new LinkedBlockingQueue<Runnable>(100000) {
                    boolean threshold() { // 让线程池创建(除核心线程外)新的线程的临界条件
                        ThreadPoolExecutor e = ((ThreadPoolExecutor) Devourer.this.exec);
                        int ps;
                        return lock.limit > e.getCorePoolSize() && (ps = e.getPoolSize()) < e.getMaximumPoolSize() && e.getActiveCount() >= ps;
                    }
                    @Override
                    public boolean offer(Runnable r) { return !threshold() && super.offer(r); }
                },
                new ThreadFactory() {
                    final AtomicInteger i = new AtomicInteger();
                    @Override
                    public Thread newThread(Runnable r) { return new Thread(r, Devourer.this.key + "-" + i.incrementAndGet()); }
                }) : exec;
    }

    /**
     * 创建对列
     * @param key 对列标识
     */
    public Devourer(String key) { this(key, null); }

    public Devourer() { this(null, null); }


    /**
     * 任务入对列
     * @param fn 任务函数
     * @return {@link Devourer}
     */
    public Devourer offer(Runnable fn) {
        if (fn == null) return this;
        if (useLast) waiting.clear();
        waiting.offer(fn);
        trigger();
        return this;
    }


    /**
     * 不断的从 {@link #waiting} 对列中取出执行
     */
    protected void trigger() {
        if (pauseCondition != null) { // 是否设置了暂停
            if (pauseCondition.test(this)) return;
            else pauseCondition = null;
        }
        if (waiting.isEmpty()) return;
        // 1.必须保证正在执行的函数不超过 parallelLimit
        // 2.必须保证这里waiting对列中不为空
        // 3.必须保证不能出现情况: waiting 对列中有值, 但没有被执行
        if (!lock.tryLock()) return;
        exec.execute(() -> {
            final long start = perSpend == null ? 0 : System.currentTimeMillis(); // 执行开始时间, 用于计算执行花费时长
            Runnable task = null;
            try {
                task = waiting.poll();
                if (task != null) task.run();
            } catch (Throwable ex) {
                if (task != null && failMaxKeep != null && failMaxKeep > 0 && (getWaitingCount() < failMaxKeep)) waiting.addFirst(task);
                if (errorHandler != null) {
                    try {
                        errorHandler.accept(ex, this);
                    } catch (Throwable exx) {
                        log.error(key + " errorHandler error", exx);
                    }
                } else {
                    log.error(key, ex);
                }
            } finally {
                // 速度限制
                if (perSpend != null && perSpend > 0) {
                    // 剩余时长 = 应该花费时长 - 已花费时长
                    long left = perSpend - (System.currentTimeMillis() - start);
                    if (left > 1) {
                        try {
                            Thread.sleep(left - 1);
                        } catch (InterruptedException e) {
                            log.error(key + " speed sleep error", e);
                        }
                    }
                }
                lock.release();
                if (!waiting.isEmpty()) trigger(); // 持续不断执行对列中的任务
            }
        });
    }


    /**
     * 设置并发数
     * @param parallel >=1
     * @return {@link Devourer}
     */
    public Devourer parallel(int parallel) {
        if (parallel < 1) throw new IllegalArgumentException("Param parallel >= 1");
        lock.limit(parallel);
        return this;
    }


    /**
     * 速度限制
     * 线程会按一定频率sleep
     * @param speed /s, /m, /h, /d; null: 不阳速
     * @return {@link Devourer}
     */
    public Devourer speed(String speed) {
        if (speed == null) {
            perSpend = null;
            return this;
        }
        String[] arr = speed.split("/");
        // 速度大小
        final int limit = Integer.valueOf(arr[0].trim());
        if (limit < 1) throw new IllegalArgumentException("speed must > 0");
        // 速度单位
        final String unit = arr[1].trim().toLowerCase();
        if (!Arrays.asList("s", "m", "h", "d").contains(unit)) {
            throw new IllegalArgumentException("speed format 10/s, 10/m, 10/h, 10/d");
        }
        // 单位时间长
        long unitDuration = 0;
        if ("s".equals(unit)) unitDuration = 1000;
        else if ("m".equals(unit)) unitDuration = 1000 * 60;
        else if ("h".equals(unit)) unitDuration = 1000 * 60 * 60;
        else if ("d".equals(unit)) unitDuration = 1000 * 60 * 60 * 24;
        perSpend = unitDuration / limit;
        return this;
    }


    /**
     * 排对个数
     * @return 排对个数
     */
    public int getWaitingCount() { return waiting.size(); }


    /**
     * 错误处理
     * @param handler 错误处理器
     * @return {@link Devourer}
     */
    public Devourer errorHandle(BiConsumer<Throwable, Devourer> handler) {
        this.errorHandler = handler;
        return this;
    }


    /**
     * 执行失败时, 保留最大个数
     * NOTE: 失败的任务会不断的重试执行, 直到成功或者对列中的个数大于此值被删除
     * 典型应用: 数据上报场景
     * @return {@link Devourer}
     */
    public Devourer failMaxKeep(Integer maxKeep) { this.failMaxKeep = maxKeep; return this; }


    /**
     * 暂停一段时间
     * NOTE 继续执行条件: 必须有新的任务入对, 或者手动调用 {@link #resume()}
     * @param duration 一段时间
     * @return {@link Devourer}
     */
    public Devourer suspend(Duration duration) {
        pauseCondition = new Predicate<Devourer>() {
            final Pause pause = new Pause(duration);
            @Override
            public boolean test(Devourer devourer) { return !pause.isTimeout(); }
        };
        return this;
    }


    /**
     * 设置暂停条件
     * 使用 {@link #resume()} 恢复
     * @param pauseCondition 暂停条件
     * @return {@link Devourer}
     */
    public Devourer suspend(Predicate<Devourer> pauseCondition) {
        this.pauseCondition = pauseCondition;
        return this;
    }


    /**
     * 手动恢复执行
     * @return {@link Devourer}
     */
    public Devourer resume() { pauseCondition = null; trigger(); return this; }


    /**
     * 是否只使用队列最后一个, 清除队列前面的任务
     * 适合: 入队的频率比出队高, 前面的任务可有可无
     */
    public Devourer useLast(boolean useLast) { this.useLast = useLast; return this; }


    /**
     * 是否是暂停状态
     * @return true: 暂停中
     */
    public boolean isSuspended() {
        try {
            if (pauseCondition != null && pauseCondition.test(this)) return true;
        } catch (NullPointerException npt) { /** trigger方法并发有可能把pause置null **/ }
        return false;
    }


    /**
     * 正在执行的任务数
     */
    public int parallel() { return lock.getLatchSize(); }


    /**
     * 关闭
     */
    public void shutdown() { if (exec instanceof ExecutorService) ((ExecutorService) exec).shutdown(); }


    @Override
    public String toString() {
        return key + "{parallel: " + parallel() + ", waitingCount: " + getWaitingCount() + ", isSuspended: "+ isSuspended() +"}";
    }
}
