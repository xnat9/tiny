package cn.xnatural.app;

import cn.xnatural.enet.event.EC;
import cn.xnatural.enet.event.EL;
import cn.xnatural.enet.event.EP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * 服务模板类
 */
public class ServerTpl {
    protected final Logger log = LoggerFactory.getLogger(getClass().getName().contains("$") ? getClass().getSuperclass() : getClass());
    /**
     * 服务名字标识.(保证唯一)
     * 可用于命名空间:
     * 1. 可用于属性配置前缀
     * 2. 可用于事件名字前缀
     */
    protected final String name;
    /**
     * 1. 当此服务被加入核心时, 此值会自动设置为核心的EP.
     * 2. 如果要服务独立运行时, 请手动设置
     */
    @Inject protected EP ep;
    private final Lazier<AppContext> _app = new Lazier<>(() -> bean(AppContext.class));
    private final Lazier<ExecutorService> _exec = new Lazier<>(() -> bean(ExecutorService.class));


    public ServerTpl(String name) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Param name required");
        this.name = name;
    }
    public ServerTpl() {
        String n = getClass().getName().contains("$") ? getClass().getName() : getClass().getSimpleName();
        this.name = Character.toLowerCase(n.charAt(0)) + (n.length() > 1 ? n.substring(1) : "");
    }

    /**
     * bean 容器. {@link #localBean}
     */
    protected Map<String, Object> beanCtx;
    @EL(name = {"bean.get", "{name}.bean.get"})
    protected <T> T localBean(EC ec, Class<T> bType, String bName) {
        if (beanCtx == null) return null;

        Object bean = null;
        if (bName != null && bType != null) {
            bean = beanCtx.get(bName);
            if (bean != null && !bType.isAssignableFrom(bean.getClass())) bean = null;
        } else if (bName != null && bType == null) {
            bean = beanCtx.get(bName);
        } else if (bName == null && bType != null) {
            if (bType.isAssignableFrom(getClass())) bean = this;
            else {
                for (Iterator<Map.Entry<String, Object>> it = beanCtx.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, Object> e = it.next();
                    if (bType.isAssignableFrom(e.getValue().getClass())) {
                        bean = e.getValue(); break;
                    }
                }
            }
        }
        return (T) bean;
    }


    /**
     * 本地查找 对象
     * @param bType 对象类型
     * @param bName 对象名字
     * @return bean
     */
    protected <T> T localBean(Class<T> bType, String bName) { return localBean(null, bType, bName); }

    /**
     * 本地查找 对象
     * @param bType 对象类型
     * @return bean
     */
    protected <T> T localBean(Class<T> bType) { return localBean(null, bType, null); }


    /**
     * 全局查找 bean
     * @param type bean类型
     * @param name bean名字
     * @return bean
     */
    protected <T> T bean(Class<T> type, String name) {
        return ep == null ? null : (T) ep.fire(new EC("bean.get", this).sync().args(type, name));
    }

    /**
     * 全局查找 bean
     * @param type bean类型
     * @return bean
     */
    protected <T> T bean(Class<T> type) { return bean(type, null); }


    /**
     * 异步执行. 拦截异常
     * @param fn 异步执行的函数
     * @param exFn 错误处理函数
     */
    public ServerTpl async(Runnable fn, Consumer<Throwable> exFn) {
        _exec.get().execute(() -> {
            try {fn.run();} catch (Throwable ex) {
                if (exFn != null) exFn.accept(ex);
                else log.error("", ex);
            }
        });
        return this;
    }
    /**
     * 异步执行. 拦截异常
     * @param fn 异步执行的函数
     */
    public ServerTpl async(Runnable fn) { return async(fn, null); }


    /**
     * 对列执行
     * @param qName 对列名, 默认当前server名称
     * @param fn 要执行的函数
     * @return {@link Devourer}当前对列
     */
    public Devourer queue(String qName, Runnable fn) { return _app.get().queue(qName, fn); }
    /**
     * 获取执行对列
     * @param qName 对列名
     * @return {@link Devourer}当前对列
     */
    public Devourer queue(String qName) { return _app.get().queue(qName, null); }
    /**
     * 对列执行, 加入到当前{@link ServerTpl#name}为对列名的对列
     * @param fn 要执行的函数
     * @return {@link Devourer}当前对列
     */
    public Devourer queue(Runnable fn) { return _app.get().queue(name, fn); }


    /**
     * 暴露 bean 给其它模块用. {@link #localBean}
     * @param bean bean实例
     * @param names bean 名字
     */
    protected ServerTpl exposeBean(Object bean, String...names) {
        if (bean == null) return this;
        if (beanCtx == null) beanCtx = new HashMap<>(7);
        if (names == null || names.length < 1) {
            String n = bean.getClass().getName().contains("$") ? bean.getClass().getName() : bean.getClass().getSimpleName();
            names = new String[]{Character.toLowerCase(n.charAt(0)) + (n.length() > 1 ? n.substring(1) : "")};
        }
        for (String n : names) {
            if (beanCtx.get(n) != null) log.warn("Override exist bean name '{}'", n);
            beanCtx.put(n, bean);
        }
        ep.addListenerSource(bean); _app.get().inject(bean);
        return this;
    }


    /**
     * 当前应用上下文
     * @return {@link AppContext}
     */
    public AppContext app() { return _app.get(); }


    /**
     * 线程池
     * @return {@link ExecutorService}
     */
    protected ExecutorService exec() { return _exec.get(); }


    /**
     * 服务名
     * @return 服务名
     */
    public String getName() { return name; }


    /**
     * 当前服务的属性集
     * @return 属性集
     */
    public Map<String, Object> attrs() { return app().attrs(name); }


    /**
     * 设置属性
     * @param aName 属性名
     * @param aValue 属性值
     * @return {@link ServerTpl}
     */
    public ServerTpl setAttr(String aName, Object aValue) {
        app().env().put(name+ "." +aName, aValue);
        return this;
    }


    /**
     * 获取属性
     * @param key 属性key
     * @param type 值类型
     * @param defaultValue 默认值
     * @return 属性值
     */
    public <T> T getAttr(String key, Class<T> type, T defaultValue) {
        Object obj = null;
        for (Map.Entry<String, Object> entry : app().env().entrySet()) {
            if ((name + "." + key).equals(entry.getKey())) {
                obj = entry.getValue(); break;
            }
        }
        T v = Utils.to(obj, type);
        if (v == null) return defaultValue;
        return v;
    }


    protected Long getLong(String key, Long defaultValue) { return getAttr(key, Long.class, defaultValue); }

    protected Integer getInteger(String key, Integer defaultValue) { return getAttr(key, Integer.class, defaultValue); }

    protected Double getDouble(String key, Double defaultValue) { return getAttr(key, Double.class, defaultValue); }

    protected Float getFloat(String key, Float defaultValue) { return getAttr(key, Float.class, defaultValue); }

    protected String getStr(String key, String defaultValue) { return getAttr(key, String.class, defaultValue); }

    protected Boolean getBoolean(String key, Boolean defaultValue) { return getAttr(key, Boolean.class, defaultValue); }
}
