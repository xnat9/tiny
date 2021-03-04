package cn.xnatural.app;

import java.time.Duration;


/**
 * 暂停器
 */
public class Pause {
    /**
     * 开始时间
     */
    protected long     start = System.currentTimeMillis();
    /**
     * 暂停时长
     */
    protected Duration duration;

    public Pause(Duration duration) {
        if (duration == null) throw new NullPointerException("Param duration required");
        this.duration = duration;
    }


    /**
     * 暂停时间是否已过
     * @return true: 时间已过
     */
    public boolean isTimeout() { return left() < 0; }


    /**
     * 剩余时长(单位:ms)
     * @return 小于等于0: 没有剩余时时, 大于0: 剩余时长
     */
    public long left() { return (start + duration.toMillis()) - System.currentTimeMillis(); }
}
