package cn.xnatural.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用执行上下文
 * 1. 应用执行环境属性 {@link #env}
 * 2. 应用执行公用唯一线程池 {@link #exec}
 * 3. 应用事件中心 {@link #ep}
 * 4. 应用所有服务实例 {@link #sourceMap}
 */
public class AppContext {
    protected static final Logger log = LoggerFactory.getLogger(AppContext.class);
}
