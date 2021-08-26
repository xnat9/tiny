package cn.xnatural.app.v;

/**
 * V 链{@link VChain}中的处理器节点
 */
public interface VProcessor {

    /**
     * V 链中先执行
     * @param input 入参
     * @return 返回
     */
    Object pre(Object input);

    /**
     * V 链中后执行
     * @param input 入参
     * @return 返回
     */
    Object post(Object input);
}
