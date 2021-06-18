# 介绍
轻量级java应用框架模板. 基于 [enet](https://gitee.com/xnat/enet)

> 系统只一个公用线程池: 所有的执行都被抽象成Runnable加入到公用线程池中执行

> 所以系统性能只由线程池大小属性 sys.exec.corePoolSize=4, 和 jvm内存参数 -Xmx512m 控制


![Image text](https://gitee.com/xnat/tmp/raw/master/img/app-desc.png)


# 系统事件
+ sys.inited:  应用始化完成(环境配置, 事件中心, 系统线程池)
+ sys.starting: 通知所有服务启动. 一般为ServerTpl
+ sys.started: 应用启动完成
+ sys.stopping: 应用停止事件(kill pid)


# 安装教程
```xml
<dependency>
    <groupId>cn.xnatural.app</groupId>
    <artifactId>app</artifactId>
    <version>1.0.3</version>
</dependency>
```

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

## 对列执行器(Devourer)
> 当需要按顺序控制任务 一个一个, 两个两个... 的执行时

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

## 汇聚流: ConvergeInputStream
> 顺序汇聚 多个流到一个流 直到 结束
* 流1(InputStream)    |
* 流2(InputStream)    | ==> 汇聚流 ==> 读取
* 流3(InputStream)    |

## 延迟对象 LazySupplier
> 封装是一个延迟计算值(只计算一次)
```java
final LazySupplier<String> _id = new LazySupplier<>(() -> {
    String id = getHeader("X-Request-ID");
    if (id != null && !id.isEmpty()) return id;
    return UUID.randomUUID().toString().replace("-", "");
});
```
延迟获取属性值
```java
final LazySupplier<String> _name = new LazySupplier<>(() -> getAttr("sys.name", String.class, "app"));
```


## http客户端
```java
// get
Utils.http().get("http://xnatural.cn:9090/test/cus?p2=2")
        .param("p1", 1)
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
[AppTest](https://gitee.com/xnat/app/blob/master/src/test/main/java/AppTest.java)

[rule](https://gitee.com/xnat/rule)d


# 参与贡献

xnatural@msn.cn
