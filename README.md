### 介绍
轻量级java应用框架模板. 基于 [enet](https://gitee.com/xnat/enet)

### 系统事件
* sys.inited:  应用始化完成(环境配置, 事件中心, 系统线程池)
* sys.starting: 通知所有服务启动. 一般为ServerTpl
* sys.started: 应用启动完成

### 安装教程
```
<dependency>
    <groupId>cn.xnatural.app</groupId>
    <artifactId>app</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 可搭配其它服务
[http](https://gitee.com/xnat/http), [jpa](https://gitee.com/xnat/jpa),
[sched](https://gitee.com/xnat/sched), [remoter](https://gitee.com/xnat/remoter)

### 初始化
```
AppContext app = new AppContext(); // 创建一个应用
app.addSource(new ServerTpl("server1") { // 添加服务 server1
    @EL(name = "sys.starting")
    void start() {
        log.info("{} start", name);
    }
});
app.addSource(TestService()); // 添加自定义service
app.start();
```

#### 添加http服务
web.hp=:8080
```
app.addSource(new ServerTpl("web") { //添加http服务
    HttpServer server;
    @EL(name = "sys.starting", async = true)
    void start() {
        server = new HttpServer(app.attrs(name), exec());
        server.buildChain(chain -> {
            chain.get("get", hCtx -> {
                hCtx.render("xxxxxxxxxxxx");
            });
        }).start();
    }
    @EL(name = "sys.stop")
    void stop() {
        if (server != null) server.stop();
    }
});
```

#### 添加jpa
jpa_local.url=jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root
```
app.addSource(new ServerTpl("jpa_local") { //数据库 jpa_local
    Repo repo;
    @EL(name = "sys.starting", async = true)
    void start() {
        repo = new Repo(attrs()).init();
        exposeBean(repo);
        ep.fire(name + ".started");
    }

    @EL(name = "sys.stopping", async = true)
    void stop() { if (repo != null) repo.close(); }
});
```

#### 动态添加服务
```
@EL(name = "sys.inited')
void sysInited() {
    if (!app.attrs("redis").isEmpty()) { //根据配置是否有redis,创建redis客户端工具
        app.addSource(new RedisClient())
    }
}
```

#### bean注入
```
app.addSource(new ServerTpl() {
    @Named ServerTpl server1; //自动注入, 按类型和名字
    @Inject Repo repo;  //自动注入, 按类型

    @EL(name = "sys.starting")
    void start() {
        log.info("{} ========= {}", name, server1.getName());
    }
});
```

#### 动态bean获取
```
app.addSource(new ServerTpl() {
    @EL(name = "sys.started")
    void start() {
        log.info(bean(Repo).firstRow("select count(1) as total from test").get("total").toString());
    }
});
```

#### 应用例子
[AppTest](https://gitee.com/xnat/app/raw/master/src/test/main/java/AppTest.java)

[GY](https://gitee.com/xnat/app/raw/master/src/test/main/java/AppTest.java)

### 参与贡献

xnatural@msn.cn
