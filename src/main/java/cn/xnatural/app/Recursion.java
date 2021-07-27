package cn.xnatural.app;


import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;


/**
 * java无限递归优化实现
 * https://www.cnblogs.com/invoker-/p/7723420.html
 * https://www.cnblogs.com/invoker-/p/7728452.html
 * @param <T>
 */
@FunctionalInterface
public interface Recursion<T> {
    /**
     * 用于递归栈帧之间的连接,惰性求值
     * @return 下一个递归栈帧
     */
    Recursion<T> apply();


    /**
     * 判断当前递归是否结束
     * @return 默认为false,因为正常的递归过程中都还未结束
     */
    default boolean isFinished(){ return false; }


    /**
     * 获得递归结果,只有在递归结束才能调用,这里默认给出异常,通过工具类的重写来获得值
     * @return 递归最终结果
     */
    default T getResult()  {
        if (!isFinished()) throw new RuntimeException("递归还没有结束,调用获得结果异常!");
        else return null;
    }


    /**
     * 及早求值,执行者一系列的递归,因为栈帧只有一个,所以使用findFirst获得最终的栈帧,接着调用getResult方法获得最终递归值
     * @return 及早求值,获得最终递归结果
     */
    default T invoke() {
        return Stream.iterate(this, Recursion::apply)
                .filter(Recursion::isFinished)
                .findFirst()
                .get()
                .getResult();
    }


    /**
     * 统一结构的方法,获得当前递归的下一个递归
     *
     * @param nextFrame 下一个递归
     * @param <T>       T
     * @return 下一个递归
     */
    static <T> Recursion<T> call(final Recursion<T> nextFrame) { return nextFrame; }


    /**
     * 结束当前递归，重写对应的默认方法的值,完成状态改为true,设置最终返回结果,设置非法递归调用
     *
     * @param value 最终递归值
     * @param <T>   T
     * @return 一个isFinished状态true的尾递归, 外部通过调用接口的invoke方法及早求值, 启动递归求值。
     */
    static <T> Recursion<T> done(T value) {
        return new Recursion<T>() {
            @Override
            public Recursion<T> apply() { throw new RuntimeException("递归已经结束,非法调用apply方法"); }

            @Override
            public boolean isFinished() { return true; }

            @Override
            public T getResult() { return value; }
        };
    }


    /**
     * 备忘录模式 函数封装
     * @param function 递归策略算法
     * @param input 输入值
     * @param <I> 输出值类型
     * @param <R> 返回值类型
     * @return 将输入值输入递归策略算法，计算出的最终结果
     */
    static <I, R> R memo(final BiFunction<Function<I, R>, I, R> function, final I input) {
        final Function<I, R> memoFn = new Function<I, R>() {
            final Map<I, R> cache = new HashMap<>();
            @Override
            public R apply(final I input) {
                return cache.computeIfAbsent(input, key -> function.apply(this, key));
            }
        };

        return memoFn.apply(input);
    }
}
