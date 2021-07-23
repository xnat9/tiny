# 介绍
轻量级java应用异步框架. 基于 [enet](https://gitee.com/xnat/enet)

> 系统只一个公用线程池: 所有的执行都被抽象成Runnable加入到公用线程池中执行

> 上层业务不需要创建线程池和线程增加复杂度, 而使用Devourer控制执行并发

> 所以系统性能只由线程池大小属性 sys.exec.corePoolSize=4, 和 jvm内存参数 -Xmx512m 控制

<!--
@startuml
skinparam ConditionEndStyle hline
split
   -[hidden]->
   :服务 1;
split again
   -[hidden]->
   :服务 2;
split again
   -[hidden]->
   :服务 n;
end split

if (任务(Runnable)) then (对列执行器/吞噬器)
  fork
    -> 并发 ;
    :Devourer 1;
    :one-by-one;
    -> 提交 ;
  fork again
    -> 并发 ;
    :Devourer 2;
    :two-by-two;
    -> 提交 ;
  fork again
    -> 并发 ;
    :Devourer n;
    :n-by-n;
    -> 提交 ;
  end fork
else
  fork
    :任务 1;
  fork again
    :任务 2;
  fork again
    :任务 n;
  end fork
endif
:线程池;
@enduml
-->
![Image text](http://www.plantuml.com/plantuml/png/fPBFIiD04CRl-nHpR0z1scCMIa5z0JsAXoqxDKktioNPA7q3_oYj9oc8M13iGH0lGjk3BzDDVGmt9gAY1C7JPBxloszsbcqdLiGsxMkMz1GDH2pwi6b8AgiCRPFSjKED46b5o9A1LfO1GB0NAIcHzeDMteRPzOKxdKA35n4G1q9HHR3vro1nXYIX6CnK5sghvT8RjPsKI7GqrkjW8oIekSUvExxAJkvVf-TkCjjmunitUV1VTS_hchYNOo5eWPi_kz4byFS-tC93ayOOGwCK367G6GQ-y8y_ij5ujRW3NeBAGrVZcgLWZqoEy-LVE2e5oc7q6mf95ckYJl3hoc5nOz3uCV3JQrPuz9rEKdKP2zUBb_NiB7kwvQpjDVz-tW00)

# 安装教程
```xml
<dependency>
    <groupId>cn.xnatural.app</groupId>
    <artifactId>app</artifactId>
    <version>1.0.4</version>
</dependency>
```

# 系统事件
+ sys.inited:  应用始化完成(环境配置, 事件中心, 系统线程池)
+ sys.starting: 通知所有服务启动. 一般为ServerTpl
+ sys.started: 应用启动完成
+ sys.stopping: 应用停止事件(kill pid)

# 可搭配其它服务
[http](https://gitee.com/xnat/http), [jpa](https://gitee.com/xnat/jpa),
[sched](https://gitee.com/xnat/sched), [remoter](https://gitee.com/xnat/remoter)

# 初始化
```java
final AppContext app = new AppContext(); // 创建一个应用
app.addSource(new ServerTpl("server1") { // 添加服务 server1
    @EL(name = "sys.starting")
    void start() {
        log.info("{} start", name);
    }
});
app.addSource(new TestService()); // 添加自定义服务
app.start(); // 应用启动
```

<!--
@startuml
class AppContext {
  #EP ep
  #Map<String, Object> sourceMap
  #Map<String, Devourer> queues

  +addSource(Object source)
  +start()
}

note left of AppContext::ep
  事件中心
end note

note right of AppContext::sourceMap
  所有被添加的Server
end note

note left of AppContext::queues
  所有被Server#queue 方添加的
  对列执行器
end note

note right of AppContext::addSource
  添加服务
end note

note left of AppContext::start
1. 触发事件 sys.inited 系统配置加载完成, 线程池, 事件中心初始化已完成
2. 触发事件 sys.starting 调用所有服务所包含@EL(name = "sys.starting") 的监听器方法
3. sys.starting 所有监听执行完成后, 继续触发 sys.started 事件
end note

class EP {
  #{field} Map<String, Listener> lsMap;
  +{method} fire(String 事件名, Object...参数);
}

note left of EP::lsMap
  所有监听器映射: 关联@EL方法
end note

note right of EP::fire
  事件触发
end note

AppContext <|-- EP
EP <|-- Server1 : 收集监听器
EP <|-- Server2 : 收集监听器
EP <|-- Server3 : 收集监听器


class Server1 {
  ...启动监听...
  @EL(name = "sys.starting")
  void start()
  
  ...停止监听...
  @EL(name = "sys.stopping")
  void stop()

  ...其它监听...
  @EL(name = "xx")
  {method} void xx()

  ...基本功能...

  {method} bean(Class bean类型, String bean名)

  {method} queue(String 对列名, Runnabel 任务)

  {method} async(Runnable 异步任务)

  {method} get[Integer, Long, Boolean, Str](String 属性名)
}

class Server2  {
  ...启动监听...
  @EL(name = "sys.starting")
  void start()

  ...其它监听...
  @EL(name = "oo")
  {method} String oo()

  ...基本功能...

  {method} bean(Class bean类型, String bean名)

  {method} queue(String 对列名, Runnabel 任务)

  {method} async(Runnable 异步任务)

  {method} get[Integer, Long, Boolean, Str](String 属性名)
}

class Server3  {  
  ...停止监听...
  @EL(name = "sys.stopping")
  void stop()

  ...其它监听...
  @EL(name = "aa")
  {method} aa()

  ...基本功能...

  {method} bean(Class bean类型, String bean名)

  {method} queue(String 对列名, Runnabel 任务)

  {method} async(Runnable 异步任务)

  {method} get[Integer, Long, Boolean, Str](String 属性名)
}
@enduml
-->

![Image text](http://www.plantuml.com/plantuml/png/vLJDJjjE7BpxANw2Iw9_Y0JS0dz4ItEeH5LKZbKF9ju4LuutjHqKH960a0gIK884z8EK3wGsaI1y50aARiJBPDVXBRhs6kDGe4YLUk6stfsPdPsTTRzkY9gHJYf2J15r7HwbKWDODL36W0a1e3qw12Xb3vw9gTvXGvFLH0YUZxn6CQCFT9pMOeYjN0SyGMDi2Mbzy2QDqaWN6E0_KPA67KA0yrrwq5vpN0I2mgGWgDX0eA2u0JZkinE9E3uQPuM6UTpuKIFdMG6f4jXmbwJ9YT7VM7wFT7wAbkURsplqn2JvJUlpx33Inf3c2TsnEp-8NuHpsvq5eAkddYW3aVrJClU1pbUQMqNogNelfru-ZC-rQ7c1vBVkuyx9J-WCGxFoZImkyPH07zV3iYeRI0BhoBJCZOlSWbNVOyhDUfti5UbSAGJMsRbLBT33pL1Bk6Jk2waKI76Ld7pdKA7h1dbdOtRdq3p8MijL7WxtpSQac2EbdVxeO40LamZ-XpO_foq8B2rhROcKTbb8TeH7Aq9tk5MOIt8K3vJR8QNtpBnPiSmQTtL5Gv9x55zqlDxH8LxhYRYC56aI_AKTb7K3gNPf5PtDzzYzd4WYOnGpO5pMK80ZNMrIMhXy2U5mc2pEq9M3OC_r1hCT8n55z_Vlwi0VDyd1R0H8xgWvlSnIuWdSKXOkPVlmdW4_jm_lUxszRpiw64E83l4XRsidH80k7r-ilVDSN4Dq_H7HVGF2pTVRnGxPJgMqJ_9L3cEVRFBsBh35CInBSFah070rfikqjdst1awbMZNO39Dm1NB7P2zxcq0cuz2yYtRucSmLU-ECbdT9VgEPhTjiFtO4YMfWm3wuCxGEJR9U206lYJF5IXBqK_Z_q2sI-vTmYlGYhQhY25AWOPhixRIIH7rSZGKuH450VixGsjURW0bal7og6YY1DDPdRBVwCSOAC-AuUkLjVBXEfogEkSdMg-k2lx-x-mMFSMlmhZMC7shqtKpj7vLU55kp5yK757e_KgLqKla5)

## 添加http服务
> web.hp=:8080
```java
app.addSource(new ServerTpl("web") { //添加web服务
    HttpServer server;
    @EL(name = "sys.starting", async = true)
    void start() {
        server = new HttpServer(app().attrs(name), exec());
        server.buildChain(chain -> {
            chain.get("get", hCtx -> {
                hCtx.render("xxxxxxxxxxxx");
            });
        }).start();
    }
    @EL(name = "sys.stopping")
    void stop() {
        if (server != null) server.stop();
    }
});
```

## 添加jpa
> jpa_local.url=jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root
```java
app.addSource(new ServerTpl("jpa_local") { //数据库 jpa_local
    Repo repo;
    @EL(name = "sys.starting", async = true)
    void start() {
        repo = new Repo(attrs()).init();
        exposeBean(repo); // 把repo暴露给全局
        ep.fire(name + ".started");
    }

    @EL(name = "sys.stopping", async = true)
    void stop() { if (repo != null) repo.close(); }
});
```

## 服务基础类: ServerTpl
> 推荐所有被加入到AppContext中的服务都是ServerTpl的子类
```java
app.addSource(new ServerTpl("服务名") {
    
    @EL(name = "sys.starting", async = true)
    void start() {
        // 初始化服务
    }
})
```
### 依赖注入
> 每个服务都是一个bean容器, AppContext是全局bean容器
* 暴露一个bean对象
  ```java
  Repo repo = new Repo("jdbc:mysql://localhost:3306/test?user=root&password=root").init();
  exposeBean(repo);
  ```
* 获取bean对象: 先从全局查找, 再从每个服务中获取
  ```java
    bean(Repo).firstRow("select count(1) as total from db").get("total")
  ```
### 异步任务
```java
async(() -> {
    // 异步执行任务
})
```
### 创建任务对列
```java
queue("toEs", () -> {
    // 提交数据到es
})
```

## 动态添加服务
```java
@EL(name = "sys.inited")
void sysInited() {
    if (!app.attrs("redis").isEmpty()) { //根据配置是否有redis,创建redis客户端工具
        app.addSource(new RedisClient())
    }
}
```

## 系统心跳
> 需要用 [sched](https://gitee.com/xnat/sched) 添加监听
```java
@EL(name = "sched.after")
void after(Duration duration, Runnable fn) {sched.after(duration, fn);}
```
> 每隔一段时间触发一次心跳, 1~4分钟随机心跳
> + 配置(sys.heartbeat.minInterval) 控制心跳最小时间间隔
> + 配置(sys.heartbeat.randomInterval) 控制心跳最大时间间隔

## bean注入
```java
app.addSource(new ServerTpl() {
    @Named ServerTpl server1; //自动注入, 按类型和名字
    @Inject Repo repo;  //自动注入, 按类型

    @EL(name = "sys.started", async = true)
    void init() {
        log.info("{} ========= {}", name, server1.getName());
    }
});
```

## 动态bean获取
```java
app.addSource(new ServerTpl() {
    @EL(name = "sys.started", async = true)
    void start() {
        log.info(bean(Repo).firstRow("select count(1) as total from test").get("total").toString());
    }
});
```

## 环境配置
* 系统属性(-Dconfigdir): configdir 指定配置文件路径. 默认:类路径
* 系统属性(-Dconfigname): configname 指定配置文件名. 默认:app
* 系统属性(-Dprofile): profile 指定启用特定的配置
* 只读取properties文件. 按顺序读取app.properties, app-[profile].properties 两个配置文件
* 配置文件支持简单的 ${} 属性替换
* 系统属性: System.getProperties() 优先级最高

## 对列执行器 Devourer
> 当需要控制任务最多 一个一个, 两个两个... 的执行时

> + 服务基础类(ServerTpl)提供方法: queue

```java
// 初始化一个 save 对列执行器
queue("save")
    .failMaxKeep(10000) // 最多保留失败的任务个数, 默认不保留
    .parallel(2) // 最多同时执行任务数, 默认1(one-by-one)
    .errorHandle {ex, me ->
        // 当任务执行抛错时执行
    };
```
```java
// 添加任务执行, 方法1
queue("save", () -> {
    // 执行任务
});
// 添加任务执行, 方法2
queue("save").offer(() -> {
    // 执行任务
});
```
```java
// 暂停执行, 一般用于发生错误时
// 注: 必须有新的任务入对, 重新触发继续执行
queue("save")
    .errorHandle {ex, me ->
        // 发生错误时, 让对列暂停执行(不影响新任务入对)
        // 1. 暂停一段时间
        me.suspend(Duration.ofSeconds(180));
        // 2. 条件暂停
        // me.suspend(queue -> true);
    };
```

### 并发流量控制锁 LatchLock
> 当被执行代码块需要控制同时线程执行的个数时
```java
final LatchLock lock = new LatchLock();
lock.limit(3); // 设置并发限制. 默认为1
if (lock.tryLock()) { // 尝试获取一个锁
    try {
        // 被执行的代码块    
    } finally {
        lock.release(); // 释放一个锁
    }
}
```

## 简单缓存 CacheSrv
```java
// 添加缓存服务
app.addSource(new CacheSrv());
```
```java
// 1. 设置缓存
bean(CacheSrv).set("缓存key", "缓存值", Duration.ofMinutes(30));
// 2. 获取缓存
bean(CacheSrv).get("缓存key");
// 3. 过期设置
bean(CacheSrv).expire("缓存key", Duration.ofMinutes(30));
// 4. 手动删除
bean(CacheSrv).remove("缓存key");
```

## 延迟对象 Lazier
> 封装是一个延迟计算值(只计算一次)
```java
final Lazier<String> _id = new Lazier<>(() -> {
    String id = getHeader("X-Request-ID");
    if (id != null && !id.isEmpty()) return id;
    return UUID.randomUUID().toString().replace("-", "");
});
```
> 延迟获取属性值
```java
final Lazier<String> _name = new Lazier<>(() -> getAttr("sys.name", String.class, "app"));
```


## http客户端
```java
// get
Utils.http().get("http://xnatural.cn:9090/test/cus?p2=2")
        .header("test", "test") // 自定义header
        .cookie("sessionId", "xx") // 自定义 cookie
        .connectTimeout(5000) // 设置连接超时 5秒
        .readTimeout(15000) // 设置读结果超时 15秒
        .param("p1", 1) // 添加参数
        .debug().execute();
```
```java
// post
Utils.http().post("http://xnatural.cn:9090/test/cus")
        .debug().execute();
```
```java
// post 表单
Utils.http().post("http://xnatural.cn:9090/test/form")
        .param("p1", "p1")
        .debug().execute();
```
```java
// post 上传文件
Utils.http().post("http://xnatural.cn:9090/test/upload")
    .param("file", new File("d:/tmp/1.txt"))
    .debug().execute();

// post 上传文件流. 一般上传大文件 可配合 汇聚流 使用
Utils.http().post("http://xnatural.cn:9090/test/upload")
  .fileStream("file", "test.md", new FileInputStream("d:/tmp/test.md"))
  .debug().execute();
```
```java
// post json
Utils.http().post("http://xnatural.cn:9090/test/json")
    .jsonBody(new JSONObject().fluentPut("p1", 1).toString())
    .debug().execute();
```
```java
// post 文本
Utils.http().post("http://xnatural.cn:9090/test/string")
        .textBody("xxxxxxxxxxxxxxxx")
        .debug().execute();
```

## Map构建器
```java
// 把bean转换成map
Utils.toMapper(bean).build();
// 添加属性
Utils.toMapper(bean).add("属性名", 属性值).build();
// 忽略属性
Utils.toMapper(bean).ignore("属性名").build();
// 转换属性
Utils.toMapper(bean).addConverter("属性名", Function<原属性值, 转换后的属性值>).build();
// 衍生属性
Utils.toMapper(bean).addConverter("属性名", "新属性", Function<原属性值, 转换后的属性值>).build();
// 忽略null属性
Utils.toMapper(bean).ignoreNull().build();
// 属性更名
Utils.toMapper(bean).aliasProp(原属性名, 新属性名).build();
// 排序map
Utils.toMapper(bean).sort().build();
// 显示class属性
Utils.toMapper(bean).showClassProp().build();
```

## 应用例子
[Demo](https://gitee.com/xnat/appdemo)

[grule](https://gitee.com/xnat/grule)

# 1.0.5 ing
- [x] LatchLock
- [x] 升enet 0.0.21
- [ ] CacheSrv accessTime

# 参与贡献

xnatural@msn.cn
