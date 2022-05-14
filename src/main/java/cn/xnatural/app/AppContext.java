package cn.xnatural.app;

import cn.xnatural.enet.event.EC;
import cn.xnatural.enet.event.EL;
import cn.xnatural.enet.event.EP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
     * see:  {@link java.util.concurrent.ThreadPoolExecutor#runWorker} 这里面当有异常抛出时 1128行代码 {@link java.util.concurrent.ThreadPoolExecutor#processWorkerExit}
     */
    protected final Lazier<ThreadPoolExecutor> _exec = new Lazier<>(() -> {
        log.debug("init sys executor ...");
        int processorCount = Runtime.getRuntime().availableProcessors();
        Integer corePoolSize = getAttr("sys.exec.corePoolSize", Integer.class, processorCount >= 4 ? 8 : 4);
        final ThreadPoolExecutor exec = new ThreadPoolExecutor(corePoolSize,
                Math.max(corePoolSize, getAttr("sys.exec.maximumPoolSize", Integer.class, processorCount <= 8 ? 16 : Math.min(processorCount * 2, 64))),
                getAttr("sys.exec.keepAliveTime", Long.class, 6L), TimeUnit.HOURS,
                new LinkedBlockingQueue<Runnable>(getAttr("sys.exec.queueCapacity", Integer.class, 100000)) {
                    /**
                     * 让线程池创建(除核心线程外)新的线程的临界条件
                     * 核心线程已满才会触发此方法
                     * 考虑点1: 系统内部添加任务, 有可能会被等待, 造成没有那么多任务的假象. 所以不能用size去比较
                     *      即: 当所有线程都处于执行状态时, 刚好有一个添加任务添加后也只是等待执行, 没有突破添加线程的条件(除非有多个添加任务)
                     *      super.size() > 1 && _exec.get().getPoolSize() < _exec.get().getMaximumPoolSize();
                     * @return true: 创建新线程
                     */
                    boolean threshold() {
                        int ps = _exec.get().getPoolSize();
                        return ps < _exec.get().getMaximumPoolSize() && _exec.get().getActiveCount() >= ps;
                    }
                    @Override
                    public boolean offer(Runnable r) { return !threshold() && super.offer(r); }
                },
                new ThreadFactory() {
                    final AtomicLong i = new AtomicLong(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "sys-" + i.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        ) {
            @Override
            public void execute(Runnable fn) {
                try {
                    super.execute(fn);
                } catch (Throwable t) {
                    log.error("sys task error", t);
                }
            }
        };
        if (getAttr("sys.exec.allowCoreThreadTimeOut", Boolean.class, false)) {
            exec.allowCoreThreadTimeOut(true);
        }
        return exec;
    });
    /**
     * 系统线程池
     * @return {@link ExecutorService}
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
                if ("sys.inited".equals(eName) || "sys.starting".equals(eName) || "sys.stopping".equals(eName) || "sys.started".equals(eName)) {
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
     * @return {@link EP}
     */
    public EP ep() { return _ep.get(); }

    /**
     * 环境属性配置.只支持properties文件, 支持${}属性替换
     * 加载顺序(优先级从小到大):
     * classpath:app.properties, classpath:app-[profile].properties
     * file:./app.properties, file:./app-[profile].properties
     * configdir:app.properties, configdir:app-[profile].properties
     * {@link #customEnv(Map)}
     * System.getProperties()
     */
    private final Lazier<Map<String, Object>> _env = new Lazier<>(() -> {
        final Map<String, Object> result = new ConcurrentHashMap<>(); // 结果属性集
        System.getProperties().forEach((k, v) -> result.put(k.toString(), v));
        String configname = (String) result.getOrDefault("configname", "app");// 配置文件名. 默认app
        String profile = (String) result.get("profile");
        String configdir = (String) result.get("configdir"); // 指定额外配置文件的目录

        //1. classpath
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(configname + ".properties")) {
            if (is != null) {
                Properties p = new Properties();
                p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                p.forEach((k, v) -> result.put(k.toString(), v));
            }
        } catch (IOException e) {
            log.error("Load classpath config file '" +configname + ".properties"+ "' error", e);
        }
        if (profile != null) {
            String fName = configname + "-" + profile + ".properties";
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(fName)) {
                if (is != null) {
                    Properties p = new Properties();
                    p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                    p.forEach((k, v) -> result.put(k.toString(), v));
                }
            } catch (IOException e) {
                log.error("Load classpath config file '" +fName+ "' error", e);
            }
        }
        //2. file:./
        try (InputStream is = new FileInputStream(configname + ".properties")) {
            Properties p = new Properties();
            p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            p.forEach((k, v) -> result.put(k.toString(), v));
        } catch (FileNotFoundException e) {
            log.trace("Load config file './" +configname + ".properties"+ "' not found");
        } catch (IOException e) {
            log.error("Load config file './" +configname + ".properties"+ "' error", e);
        }
        if (profile != null) {
            String fName = configname + "-" + profile + ".properties";
            try (InputStream is = new FileInputStream(fName)) {
                Properties p = new Properties();
                p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                p.forEach((k, v) -> result.put(k.toString(), v));
            } catch (FileNotFoundException e) {
                log.trace("Load config file './" +fName+ "' not found");
            } catch (IOException e) {
                log.error("Load config file './" +fName+ "' error", e);
            }
        }
        //3. configdir
        if (configdir != null) {
            File targetFile = new File(configdir, configname + ".properties");
            try (InputStream is = new FileInputStream(targetFile)) {
                Properties p = new Properties();
                p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                p.forEach((k, v) -> result.put(k.toString(), v));
            } catch (FileNotFoundException e) {
                log.trace("Load config file '" +targetFile.getAbsolutePath()+ "' not found");
            } catch (IOException e) {
                log.error("Load config file '" +targetFile.getAbsolutePath()+ "' error", e);
            }
            if (profile != null) {
                targetFile = new File(configdir, configname + "-" + profile + ".properties");
                try (InputStream is = new FileInputStream(targetFile)) {
                    Properties p = new Properties();
                    p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                    p.forEach((k, v) -> result.put(k.toString(), v));
                } catch (FileNotFoundException e) {
                    log.trace("Load config file '" +targetFile.getAbsolutePath()+ "' not found");
                } catch (IOException e) {
                    log.error("Load config file '" +targetFile.getAbsolutePath()+ "' error", e);
                }
            }
        }
        customEnv(result);

        // 替换 ${}
        new Runnable() {
            final Pattern pattern = Pattern.compile("(\\$\\{(?<attr>[\\w\\._]+)\\})+");
            final AtomicInteger count = new AtomicInteger(0);
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
     */
    public Map<String, Object> env() { return _env.get(); }


    /**
     * 启动
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
     * {@link #ep} 会找出source对象中所有其暴露的功能. 即: 用 {@link EL} 标注的方法
     * 注: 为每个对象源都配一个 name 属性标识
     * @param source bean 对象
     * @param name bean 名字
     * @return {@link AppContext}
     */
    public AppContext addSource(Object source, String name) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Param name required");
        if (source == null) throw new IllegalArgumentException("Param source required");
        if ("sys".equalsIgnoreCase(name) || "env".equalsIgnoreCase(name) || "log".equalsIgnoreCase(name) || "bean".equalsIgnoreCase(name)) {
            log.error("Name not allowed [sys, env, log, bean]. source: {}", source); return this;
        }
        if (sourceMap.containsKey(name)) {
            log.error("Already exist bean '{}': {}", name, sourceMap.get(name)); return this;
        }
        sourceMap.put(name, source);
        inject(source); ep().addListenerSource(source);
        return this;
    }


    /**
     * 添加对象源
     * {@link #ep} 会找出source对象中所有其暴露的功能. 即: 用 @EL 标注的方法
     * 注: 为每个对象源都配一个 name 属性标识
     * @param sources bean 对象
     * @return {@link AppContext}
     */
    public AppContext addSource(Object... sources) {
        for (Object source : sources) {
            addSource(source, source instanceof ServerTpl ? ((ServerTpl) source).name : source.getClass().getName().contains("$") ? source.getClass().getName() : source.getClass().getSimpleName());
        }
        return this;
    }


    /**
     * 加入到对列行器执行函数
     * 每个对列里面的函数同一时间只执行一个, 各对列相互执行互不影响
     * @param qName 对列名
     * @param fn 要执行的函数
     * @return {@link Devourer}
     */
    public Devourer queue(String qName, Runnable fn) {
        if (qName == null || qName.isEmpty()) throw new IllegalArgumentException("Param qName required");
        Devourer devourer = queues.get(qName);
        if (devourer == null) {
            synchronized (queues) {
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
     * 为bean对象中的{@link Inject}注解字段注入对应的bean对象
     * @param source bean
     */
    @EL(name = "inject")
    public void inject(Object source) {
        Utils.iterateField(source.getClass(), (field) -> {
            Inject inject = field.getAnnotation(Inject.class);
            if (inject == null) return;
            try {
                field.setAccessible(true);
                Object v = field.get(source);
                if (v != null) return; // 已经存在值则不需要再注入

                // 取值
                if (EP.class.isAssignableFrom(field.getType())) v = wrapEpForSource(source);
                else {
                    if (inject.name().isEmpty()) {
                        v = bean(field.getType(), field.getName());
                        if (v == null) v = bean(field.getType(), null);
                    }
                    else {
                        v = bean(field.getType(), inject.name());
                    }
                }

                if (v == null) return;
                field.set(source, v);
                log.trace("Inject field '{}' for object '{}'", field.getName(), source);
            } catch (Exception ex) {
                log.error("Inject field '" + field.getName() + "' error!", ex);
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
    @EL(name = {"bean.get", "sys.bean.get"}, order = -1f)
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
     * 当系统线程池忙的时候, 会为创建服务创建一个独用的线程池:
     *      抵御流量突发,同时保证各个业务的任务隔离(即使流量突发也不会影响其他业务导致整个系统被拖垮),
     *      另外还可以抵御线程池隔离时各个业务设置不合理导致的资源分配不均,任务阻塞或者空转问题
     * 分发新任务策略: 当系统线程池忙, 则使用服务自己的线程池; 默认都用系统线程池
     * @param source 源对象
     * @return {@link Executor}
     */
    protected Executor wrapExecForSource(Object source) {
        return new ExecutorService() {
            @Override
            public void shutdown() {}
            @Override
            public List<Runnable> shutdownNow() { return emptyList(); }
            @Override
            public boolean isShutdown() { return _exec.get().isShutdown(); }
            @Override
            public boolean isTerminated() { return _exec.get().isTerminated(); }
            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return _exec.get().awaitTermination(timeout, unit);
            }
            @Override
            public <T> Future<T> submit(Callable<T> task) { return _exec.get().submit(task); }
            @Override
            public <T> Future<T> submit(Runnable task, T result) { return _exec.get().submit(task, result); }
            @Override
            public Future<?> submit(Runnable task) { return _exec.get().submit(task); }
            @Override
            public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
                return _exec.get().invokeAll(tasks);
            }
            @Override
            public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
                return _exec.get().invokeAll(tasks, timeout, unit);
            }
            @Override
            public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
                return _exec.get().invokeAny(tasks);
            }
            @Override
            public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return _exec.get().invokeAny(tasks, timeout, unit);
            }
            @Override
            public void execute(Runnable cmd) { _exec.get().execute(cmd); }
            public int getCorePoolSize() { return _exec.get().getCorePoolSize() + (_exec.done() ? _exec.get().getCorePoolSize() : 0); }
            public int getWaitingCount() { return _exec.get().getQueue().size() + (_exec.done() ? _exec.get().getQueue().size() : 0); }

            @Override
            public String toString() { return _exec.done() ? _exec.get().toString() : "uninitialized"; }
        };
    }


    /**
     * 为每个Source包装EP
     * @param source 源对象
     * @return {@link EP}
     */
    protected EP wrapEpForSource(Object source) {
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
            public EP listen(String eName, boolean async, float order, int limit, Runnable fn) { return ep().listen(eName, async, order, limit, fn); }
            @Override
            public EP listen(String eName, boolean async, float order, int limit, Function fn) { return ep().listen(eName, async, order, limit, fn); }
            @Override
            public EP listen(String eName, boolean async, float order, int limit, BiFunction fn) { return ep().listen(eName, async, order,limit, fn); }
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
     * 额外自定义环境配置
     * @param already 已加载的属性集
     */
    protected void customEnv(Map<String, Object> already) { }


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
     * get profile
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
    protected final Lazier<String> _id = new Lazier<>(() -> getAttr("sys.id", String.class, Utils.nanoId()));
    public String id() { return _id.get(); }
}