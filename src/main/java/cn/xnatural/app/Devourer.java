package cn.xnatural.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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
     * 并发限制
     */
    protected int parallelLimit = 1;
    /**
     * 当前并发数
     */
    protected final AtomicInteger parallelLatch = new AtomicInteger(0);
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
     * 创建
     * @param key 标识
     * @param exec 线程池
     */
    public Devourer(String key, Executor exec) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("Param key not empty");
        if (exec == null) throw new IllegalArgumentException("Param executor required");
        this.key = key;
        this.exec = exec;
    }

    public Devourer() {
        this.key = Devourer.class.getSimpleName() + "@" + Integer.toHexString(hashCode());
        this.exec = Executors.newFixedThreadPool(4, new ThreadFactory() {
            final AtomicInteger i = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) { return new Thread(r, key + "-" + i.incrementAndGet()); }
        });
    }


    /**
     * 任务入对列
     * @param fn 任务函数
     * @return {@link Devourer}
     */
    public Devourer offer(Runnable fn) {
        if (fn == null) return this;
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
        int latch = parallelLatch.get();
        if (latch >= parallelLimit) return;
        if (!parallelLatch.compareAndSet(latch, latch + 1)) {
            if (parallelLatch.get() < parallelLimit) trigger();
            return;
        }
        exec.execute(() -> {
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
                        log.error(key, exx);
                    }
                } else {
                    log.error(key, ex);
                }
            } finally {
                parallelLatch.decrementAndGet();
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
        this.parallelLimit = parallel;
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
            public boolean test(Devourer devourer) {
                return !pause.isTimeout();
            }
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
     * @return 任务数
     */
    public int parallel() { return parallelLatch.get(); }


    /**
     * 关闭
     */
    public void shutdown() { if (exec instanceof ExecutorService) ((ExecutorService) exec).shutdown(); }


    @Override
    public String toString() {
        return key + "{parallel: " + parallel() + ", waitingCount: " + getWaitingCount() + ", isSuspended: "+ isSuspended() +"}";
    }
}
