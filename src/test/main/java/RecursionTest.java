import cn.xnatural.app.Recursion;
import org.junit.jupiter.api.Test;

public class RecursionTest {


    @Test
    void factorial() throws Exception {
        System.out.println(factorialTailRecursion(1, 10_000_000).invoke());
    }


    @Test
    void fibonacciTest() {
        System.out.println(fibonacciMemo(100));
    }


    /**
     * 使用同一封装的备忘录模式 执行斐波那契策略
     * @param n 第n个斐波那契数
     * @return 第n个斐波那契数
     */
    long fibonacciMemo(long n) {
        return Recursion.memo((fib, number) -> {
            if (number == 0 || number == 1) {
                new Exception().printStackTrace();
                return 1L;
            }
            return fib.apply(number -1 ) + fib.apply(number-2);
        }, n);
    }


    /**
     * 阶乘计算 -- 使用尾递归接口完成
     * @param factorial 当前递归栈的结果值
     * @param number 下一个递归需要计算的值
     * @return 尾递归接口,调用invoke启动及早求值获得结果
     */
    Recursion<Long> factorialTailRecursion(final long factorial, final long number) {
        if (number == 1) {
            // new Exception().printStackTrace();
            return Recursion.done(factorial);
        }
        else {
            return Recursion.call(() -> factorialTailRecursion(factorial + number, number - 1));
        }
    }
}
