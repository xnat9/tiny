package cn.xnatural.app.v;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * V链: 执行过程先进后出(类似V型)
 */
public class VChain {
    protected static final Logger log = LoggerFactory.getLogger(VChain.class);
    protected final LinkedList<VProcessor> ps = new LinkedList<>();


    /**
     * 执行链
     * 按v型依次执行, 上个处理结果为下个处理器的入参
     * @param input 入参
     * @return 链路执行结果
     */
    public Object run(Object input) {
        Object result = input;
        for (Iterator<VProcessor> it = ps.iterator(); it.hasNext(); ) {
            result = it.next().pre(result);
        }
        for (Iterator<VProcessor> it = ps.descendingIterator(); it.hasNext(); ) {
            result = it.next().post(result);
        }
        return result;
    }


    /**
     * 添加V处理器
     * @param vProcessor v处理器
     * @return {@link VChain}
     */
    public VChain add(VProcessor vProcessor) { ps.offer(vProcessor); return this; }
}
