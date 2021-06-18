package cn.xnatural.app;

import cn.xnatural.enet.event.EC;
import cn.xnatural.enet.event.EL;
import cn.xnatural.enet.event.EP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;

/**
 * 应用执行上下文
 * 1. 应用执行环境属性 {@link #env}
 * 2. 应用执行公用唯一线程池 {@link #exec}
 * 3. 应用事件中心 {@link #ep}
 * 4. 应用所有服务实例 {@link #sourceMap}
 */
public class AppContext {
    protected static final Logger                log          = LoggerFactory.getLogger(AppContext.class);
    /**
     * 服务对象源
     */
    protected final        Map<String, Object>   sourceMap    = new ConcurrentHashMap<>();
    /**
     * 对列执行器映射
     */
    protected final        Map<String, Devourer> queues       = new ConcurrentHashMap<>();
    /**
     * 启动时间
     */
    public final           Date                  startup      = new Date();
    /**
     * 系统负载值 0 - 10
     */
    private                Integer               sysLoad      = 0;
    /**
     * jvm关闭钩子. kill
     * System.exit(0)
     */
    protected final        Thread                shutdownHook = new Thread(() -> {
        // 通知各个模块服务关闭
        ep().fire("sys.stopping", EC.of(this).async(false).completeFn(ec -> {
            exec().shutdown();
            // 不删除的话会执行两遍
            // if (shutdownHook) Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }));
    }, "stop");

    /**
     * 初始化一个 {@link java.util.concurrent.ThreadPoolExecutor}
     * NOTE: 如果线程池在不停的创建线程, 有可能是因为 提交的 Runnable 的异常没有被处理.
     * see:  {@link java.util.concurrent.ThreadPoolExecutor#runWorker(java.util.concurrent.ThreadPoolExecutor.Worker)} 这里面当有异常抛出时 1128行代码 {@link java.util.concurrent.ThreadPoolExecutor#processWorkerExit(java.util.concurrent.ThreadPoolExecutor.Worker, boolean)}
     */
    protected final Lazier<ThreadPoolExecutor> _exec = new Lazier<>(() -> {
        log.debug("init sys executor ... ");
        Integer maxSize = getAttr("sys.exec.maximumPoolSize", Integer.class, 32);
        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                getAttr("sys.exec.corePoolSize", Integer.class, 8), maxSize,
                getAttr("sys.exec.keepAliveTime", Long.class, 4L), TimeUnit.HOURS,
                new LinkedBlockingQueue<>(maxSize * 2),
                new ThreadFactory() {
                    AtomicInteger i = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "sys-" + i.getAndIncrement());
                    }
                }
        ) {
            @Override
            protected void beforeExecute(Thread t, Runnable r) { super.beforeExecute(t, r); populateLoad(); }

            @Override
            public void execute(Runnable fn) {
                try {
                    super.execute(fn);
                } catch (RejectedExecutionException ex) {
                    log.warn("Thread pool rejected new task very heavy load. {}", this);
                } catch (Throwable t) {
                    log.error("Task Error", t);
                }
            }

            @Override
            protected void afterExecute(Runnable r, Throwable t) { super.afterExecute(r, t); populateLoad(); }

            int gap1 = getCorePoolSize() / 3;
            int gap2 = (getMaximumPoolSize() - getCorePoolSize()) / 3;

            // 计算线程池负载
            void populateLoad() {
                int ac = getActiveCount();
                if (getCorePoolSize() - ac > gap1 * 2) sysLoad = 2;
                else if (getCorePoolSize() - ac > gap1) sysLoad = 3;
                else if (getCorePoolSize() - ac > 0) sysLoad = 4;
                else if (getCorePoolSize() == ac) sysLoad = 5;
                else if (getQueue().size() > 0) sysLoad = 6;
                    // 超过核心线程的线程数在工作
                else if (getMaximumPoolSize() - ac > gap2 * 2) sysLoad = 7;
                else if (getMaximumPoolSize() - ac > gap2) sysLoad = 8;
                else if (getMaximumPoolSize() - ac > 0) sysLoad = 9;
                else if (getMaximumPoolSize() == ac) sysLoad = 10;
            }
        };
        // exec.allowCoreThreadTimeOut(true)
        return exec;
    });
    /**
     * 线程池
     * @return ExecutorService
     */
    public ExecutorService exec() { return _exec.get(); }

    /**
     * 初始化 事件中心
     */
    protected final Lazier<EP> _ep = new Lazier<>(() -> {
        log.debug("init ep ...");
        EP ep = new EP(exec(), LoggerFactory.getLogger(EP.class)) {
            @Override
            protected Object doPublish(String eName, EC ec) {
                if ("sys.starting".equals(eName) || "sys.stopping".equals(eName) || "sys.started".equals(eName)) {
                    if (ec.source() != AppContext.this) throw new UnsupportedOperationException("not allow fire event '" + eName + "'");
                }
                return super.doPublish(eName, ec);
            }

            @Override
            public String toString() { return "coreEp"; }
        };
        // 添加 ep 跟踪事件
        String track = getAttr("ep.track", String.class, null);
        if (track != null) {
            Arrays.stream(track.split(",")).filter(s -> s != null && !s.trim().isEmpty()).forEach(s -> ep.addTrackEvent(s.trim()));
        }
        ep.addListenerSource(AppContext.this);
        return ep;
    });
    /**
     * 事件中心
     * @return EP
     */
    public EP ep() { return _ep.get(); }

    /**
     * 环境属性配置.只支持properties文件, 支持${}属性替换
     */
    private final Lazier<Map<String, Object>> _env = new Lazier<>(() -> {
        Map<String, Object> result = new ConcurrentHashMap<>(); // 结果属性集
        System.getProperties().forEach((k, v) -> result.put(k.toString(), v));
        String configdir = (String) result.get("configdir"); // 配置文件的目录. 默认classpath路径
        String configname = (String) result.getOrDefault("configname", "app");// 配置文件名. 默认app
        String profile = (String) result.get("profile");
        List<String> cfgNames = new LinkedList<>();
        cfgNames.add(configname + ".properties");
        if (profile != null && !profile.trim().isEmpty()) {
            cfgNames.add(configname + "-" + profile.trim() + ".properties");
        }
        for (String name : cfgNames) {
            try (InputStream is = (configdir == null || configdir.isEmpty()) ? getClass().getClassLoader().getResourceAsStream(name) : new FileInputStream(new File(configdir, name))) {
                if (is == null) continue;
                Properties p = new Properties();
                p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                p.forEach((k, v) -> result.put(k.toString(), v));
                // new Yaml().loadAs(new InputStreamReader(is, StandardCharsets.UTF_8), Map.class).forEach((k, v) -> result.put(k.toString(), v));
            } catch (IOException e) {
                log.error("Load config file '" +name+ "' error", e);
            }
        }

        new Runnable() { // 替换 ${}
            Pattern pattern = Pattern.compile("(\\$\\{(?<attr>[\\w\\._]+)\\})+");
            AtomicInteger count = new AtomicInteger(0);
            @Override
            public void run() {
                if (count.getAndIncrement() >= 3) return;
                boolean f = false;
                for (Map.Entry<String, Object> e : result.entrySet()) {
                    if (e.getValue() == null) continue;
                    Matcher m = pattern.matcher(e.getValue().toString());
                    if (!m.find()) continue;
                    f = true;
                    result.put(e.getKey(), e.getValue().toString().replace(m.group(0), result.getOrDefault(m.group("attr"), "").toString()));
                }
                if (f) run(); // 一直解析直到所有值都被替换完成
            }
        }.run();

        System.getProperties().forEach((k, v) -> result.put(k.toString(), v));
        return result;
    });
    /**
     * 环境属性配置
     * @return map
     */
    public Map<String, Object> env() { return _env.get(); }


    /**
     * 启动
     * @return {@link AppContext}
     */
    public AppContext start() {
        log.info("Starting Application with PID {}, active profile: {}", Utils.pid(), getProfile());
        // 1. 初始化
        ep().fire("sys.inited", EC.of(this));
        // 2. 通知所有服务启动
        ep().fire("sys.starting", EC.of(this).completeFn(ec -> {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            sourceMap.forEach((s, o) -> inject(o)); // 自动注入
            log.info("Started Application '{}' in {} seconds (JVM running for {})", name() + ":" + id(), (System.currentTimeMillis() - startup.getTime()) / 1000.0, ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
            ep().fire("sys.started", EC.of(this).completeFn((ec1) -> {
                Supplier<Duration> nextTimeFn = () -> {
                    Integer minInterval = getAttr("sys.heartbeat.minInterval", Integer.class, 60);
                    Integer randomInterval = getAttr("sys.heartbeat.randomInterval", Integer.class, 180);
                    return Duration.ofSeconds(minInterval + new Random().nextInt(randomInterval));
                };
                // 每隔一段时间触发一次心跳, 1~4分钟随机心跳
                final Runnable fn = new Runnable() {
                    @Override
                    public void run() {
                        ep().fire("sys.heartbeat");
                        ep().fire("sched.after", nextTimeFn.get(), this);
                    }
                };
                fn.run();
            }));
        }));
        return this;
    }


    /**
     * 添加对象源
     * {@link #ep} 会找出source对象中所有其暴露的功能. 即: 用 @EL 标注的方法
     * 注: 为每个对象源都配一个 name 属性标识
     * @param source bean 对象
     * @param name bean 名字
     * @return {@link AppContext}
     */
    public AppContext addSource(Object source, String name) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Param name not empty");
        if (source == null) throw new IllegalArgumentException("Param source required");
        if ("sys".equalsIgnoreCase(name) || "env".equalsIgnoreCase(name) || "log".equalsIgnoreCase(name)) {
            log.error("Name property cannot equal 'sys', 'env' or 'log' . source: {}", source); return this;
        }
        if (sourceMap.containsKey(name)) {
            log.error("Name property '{}' already exist in source: {}", name, sourceMap.get(name)); return this;
        }
        sourceMap.put(name, source);
        inject(source); ep().addListenerSource(source);
        return this;
    }


    /**
     * 添加对象源
     * {@link #ep} 会找出source对象中所有其暴露的功能. 即: 用 @EL 标注的方法
     * 注: 为每个对象源都配一个 name 属性标识
     * @param source bean 对象
     * @return {@link AppContext}
     */
    public AppContext addSource(Object source) {
        return addSource(source, source instanceof ServerTpl ? ((ServerTpl) source).name : getClass().getName().contains("$") ? getClass().getSuperclass().getSimpleName() : getClass().getSimpleName());
    }


    /**
     * 加入到对列行器执行函数
     * 每个对列里面的函数同一时间只执行一个, 各对列相互执行互不影响
     * @param qName 对列名
     * @param fn 要执行的函数
     * @return {@link Devourer}
     */
    public Devourer queue(String qName, Runnable fn) {
        if (qName == null || qName.isEmpty()) throw new IllegalArgumentException("Param qName not empty");
        Devourer devourer = queues.get(qName);
        if (devourer == null) {
            synchronized (this) {
                devourer = queues.get(qName);
                if (devourer == null) {
                    devourer = new Devourer(qName, exec());
                    queues.put(qName, devourer);
                }
            }
        }
        if (fn != null) devourer.offer(fn);
        return devourer;
    }


    /**
     * 为bean对象中的{@link javax.inject.Inject}注解字段注入对应的bean对象
     * @param source bean
     */
    @EL(name = "inject", async = false)
    public void inject(Object source) {
        Utils.iterateField(source.getClass(), (field) -> {
            Inject inject = field.getAnnotation(Inject.class);
            if (inject != null) {
                try {
                    field.setAccessible(true);
                    Object v = field.get(source);
                    if (v != null) return; // 已经存在值则不需要再注入

                    // 取值
                    if (EP.class.isAssignableFrom(field.getType())) v = wrapEpForSource(source);
                    else v = bean(field.getType(), null); // 全局获取bean对象

                    if (v == null) return;
                    field.set(source, v);
                    log.trace("Inject @Inject field '{}' for object '{}'", field.getName(), source);
                } catch (Exception ex) {
                    log.error("Inject @Inject field '" + field.getName() + "' error!", ex);
                }
            }
            Named named = field.getAnnotation(Named.class);
            if (named != null) {
                try {
                    field.setAccessible(true);
                    Object v = field.get(source);
                    if (v != null) return; // 已经存在值则不需要再注入

                    // 取值
                    if (EP.class.isAssignableFrom(field.getType())) v = wrapEpForSource(source);
                    else v = bean(field.getType(), named.value().isEmpty() ? field.getName() : named.value()); // 全局获取bean对象

                    if (v == null) return;
                    field.set(source, v);
                    log.trace("Inject @Named field '{}' for object '{}'", field.getName(), source);
                } catch (Exception ex) {
                    log.error("Inject @Named field '" + field.getName() + "' error!", ex);
                }
            }
        });
    }


    /**
     * 全局查找 bean 对象
     * @param type 对象类型
     * @param name 对象名字
     * @return bean
     */
    public <T> T bean(Class<T> type, String name) { return (T) ep().fire("bean.get", EC.of(this).sync().args(type, name)); }


    /**
     * {@link #sourceMap}中查找对象
     * @param ec 事件上下文
     * @param bType bean 对象类型
     * @param bName bean 对象名字
     * @return bean 对象
     */
    @EL(name = {"bean.get", "sys.bean.get"}, async = false, order = -1f)
    protected <T> T localBean(EC ec, Class<T> bType, String bName) {
        if (ec != null && ec.result != null) return (T) ec.result; // 已经找到结果了, 就直接返回

        Object bean = null;
        if (bName != null && bType != null) {
            bean = sourceMap.get(bName);
            if (bean != null && !bType.isAssignableFrom(bean.getClass())) bean = null;
        } else if (bName != null && bType == null) {
            bean = sourceMap.get(bName);
        } else if (bName == null && bType != null) {
            if (Executor.class.isAssignableFrom(bType) || ExecutorService.class.isAssignableFrom(bType)) bean = wrapExecForSource(ec.source());
            else if (AppContext.class.isAssignableFrom(bType)) bean = this;
            else if (EP.class.isAssignableFrom(bType)) bean = wrapEpForSource(ec.source());
            else {
                for (Iterator<Map.Entry<String, Object>> it = sourceMap.entrySet().iterator(); it.hasNext(); ) {
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
     * 为 source 包装 Executor
     * @param source 源对象
     * @return {@link Executor}
     */
    protected Executor wrapExecForSource(Object source) {
        log.trace("wrapExecForSource: {}", source);
        return new ExecutorService() {
            @Override
            public void shutdown() {}
            @Override
            public List<Runnable> shutdownNow() { return emptyList(); }
            @Override
            public boolean isShutdown() { return exec().isShutdown(); }
            @Override
            public boolean isTerminated() { return exec().isTerminated(); }
            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return exec().awaitTermination(timeout, unit);
            }
            @Override
            public <T> Future<T> submit(Callable<T> task) { return exec().submit(task); }
            @Override
            public <T> Future<T> submit(Runnable task, T result) { return exec().submit(task, result); }
            @Override
            public Future<?> submit(Runnable task) { return exec().submit(task); }
            @Override
            public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
                return exec().invokeAll(tasks);
            }
            @Override
            public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
                return exec().invokeAll(tasks, timeout, unit);
            }
            @Override
            public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
                return exec().invokeAny(tasks);
            }
            @Override
            public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return exec().invokeAny(tasks, timeout, unit);
            }
            @Override
            public void execute(Runnable cmd) { exec().execute(cmd); }
            public int getCorePoolSize() { return _exec.get().getCorePoolSize(); }
            public int getWaitingCount() { return _exec.get().getQueue().size(); }
        };
    }


    /**
     * 为每个Source包装EP
     * @param source 源对象
     * @return {@link EP}
     */
    protected EP wrapEpForSource(Object source) {
        log.trace("wrapEpForSource: {}", source);
        return new EP() {
            @Override
            protected void init(Executor exec, Logger log) {}
            @Override
            public EP addTrackEvent(String... eNames) { ep().addTrackEvent(eNames); return this; }
            @Override
            public EP delTrackEvent(String... eNames) { ep().delTrackEvent(eNames); return this; }
            @Override
            public EP removeEvent(String eName, Object s) {
                if (source != null && s != null && source != s) throw new UnsupportedOperationException("Only allow remove event of this source: " + source);
                ep().removeEvent(eName, s); return this;
            }
            @Override
            public EP addListenerSource(Object s) { ep().addListenerSource(s); return this; }
            @Override
            public EP listen(String eName, Runnable fn, boolean async, float order, Integer limit) { return ep().listen(eName, fn, async, order, limit); }
            @Override
            public EP listen(String eName, Function fn, boolean async, float order, Integer limit) { return ep().listen(eName, fn, async, order, limit); }
            @Override
            public EP listen(String eName, BiFunction fn, boolean async, float order, Integer limit) { return ep().listen(eName, fn, async, order,limit); }
            @Override
            public boolean exist(String... eNames) { return ep().exist(eNames); }
            @Override
            public Object fire(String eName, EC ec) {
                if (ec.source() == null) ec.source(source);
                return ep().fire(eName, ec);
            }
            @Override
            public String toString() { return "wrappedCoreEp: " + source; }
        };
    }


    /**
     * 属性集:一组属性
     * @return 属性集
     */
    public Map<String, Object> attrs(String key) {
        Map<String, Object> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, Object> entry : env().entrySet()) {
            if (entry.getKey().startsWith(key + ".")) {
                result.put(entry.getKey().replace(key + ".", ""), entry.getValue());
            }
        }
        return result;
    }


    /**
     * 获取属性
     * @param key 属性key
     * @param type 值类型
     * @param defaultValue 默认值
     * @return 属性值
     */
    public <T> T getAttr(String key, Class<T> type, T defaultValue) {
        T v = Utils.to(env().get(key), type);
        if (v == null) return defaultValue;
        return v;
    }


    /**
     * getter
     * @return profile
     */
    public String getProfile() { return (String) env().get("profile"); }


    /**
     * 系统名字. 用于多个系统启动区别
     */
    protected final Lazier<String> _name = new Lazier<>(() -> getAttr("sys.name", String.class, "app"));
    public String name() { return _name.get(); }


    /**
     * 实例Id
     * NOTE: 保证唯一
     */
    protected final Lazier<String> _id = new Lazier<>(() -> getAttr("sys.id", String.class, Utils.random(10, name() + "_", null)));
    public String id() { return _id.get(); }


    /**
     * 负载值
     * @return 1-10
     */
    public Integer getSysLoad() { return sysLoad; }
}
