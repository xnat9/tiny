package cn.xnatural.app;

import java.util.function.Supplier;

/**
 * Groovy @Lazy 实现
 * @param <T>
 */
public class Lazier<T> implements Supplier<T> {
    private final Supplier<T> supplier;
    // 只执行一次
    private boolean       once = false;
    private T result;

    public Lazier(Supplier<T> supplier) {
        if (supplier == null) throw new NullPointerException("Param supplier is null");
        this.supplier = supplier;
    }


    /**
     * 清除
     */
    public void clear() {
        once = false;
        result = null;
    }


    @Override
    public T get() {
        if (!once) {
            synchronized (this) {
                if (!once) {
                    result = supplier.get();
                    if (result != null) once = true; //为空,则重新取
                }
            }
        }
        return result;
    }
}
