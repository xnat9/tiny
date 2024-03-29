import cn.xnatural.app.AppContext;
import cn.xnatural.app.Inject;
import cn.xnatural.app.ServerTpl;
import cn.xnatural.app.Utils;
import cn.xnatural.app.util.DB;
import cn.xnatural.enet.event.EL;
import cn.xnatural.http.HttpServer;
import cn.xnatural.remoter.Remoter;
import cn.xnatural.sched.Sched;

import java.time.Duration;
import java.util.Random;

public class AppTest {

    public static void main(String[] args) {
        new AppContext()
                .addSource(
                        new ServerTpl("server1") {
                            @Inject DB db;

                            @EL(name = "sys.starting")
                            void start() { log.info("{} start", name); }

                            @EL(name = "sys.started", async = true)
                            void started() {
                                db.execute("create table IF NOT EXISTS test(id int auto_increment, name varchar(255));");
                                db.execute("insert into test(name) values('aa');");
                                log.info("测试sql: " + db.single("select count(1) from test", Integer.class));
                            }
                        }
                        , sched()
                        , web()
                        , db()
                        , remoter()
                        , 压测()
                ).start();
    }


    /**
     * 创建数据库操作
     */
    static ServerTpl db() {
        return new ServerTpl("db_local") { //数据库
            DB DB;
            @EL(name = "sys.starting", async = true)
            void start() {
                DB = new DB(getStr("url", null));
                attrs().forEach((k, v) -> DB.dsAttr(k, v));
                exposeBean(DB, "db_local");
                ep.fire(name + ".started");
            }

            @EL(name = "sys.stopping", async = true, order = 2f)
            void stop() {
                if (DB != null) {
                    try { DB.close(); } catch (Exception e) {
                        log.error("", e);
                    }
                }
            }
        };
    }


    /**
     * 创建 web服务
     */
    static ServerTpl web() {
        return new ServerTpl("web") { //添加http服务
            HttpServer server;

            @EL(name = "sys.starting", async = true)
            void start() {
                server = new HttpServer(attrs(), exec());
                server.buildChain(chain -> {
                    chain.get("test", hCtx -> hCtx.render("xxxxxx"));
                });
                server.start();
                server.enabled = false;
            }

            @EL(name = "sys.started", async = true)
            void started() {
                for (Object ctrl : server.getCtrls()) exposeBean(ctrl);
                server.enabled = true;
            }

            @EL(name = "sys.stop")
            void stop() { if (server != null) server.close(); }
        };
    }


    /**
     * 添加时间调度服务
     */
    static ServerTpl sched() {
        return new ServerTpl("sched") { // 定时任务
            Sched sched;
            @EL(name = "sys.starting", async = true)
            void start() {
                sched = new Sched(attrs(), exec()).init();
                exposeBean(sched);
                ep.fire(name + ".started");
            }

            @EL(name = "sched.after")
            void after(Duration duration, Runnable fn) {sched.after(duration, fn);}

            @EL(name = "sys.stopping", async = true)
            void stop() { if (sched != null) sched.stop(); }
        };
    }


    static ServerTpl remoter() {
        return new ServerTpl("remoter") {
            @EL(name = "sched.started")
            void start() {
                Remoter remoter = new Remoter(app().name(), app().id(), attrs(), exec(), ep, bean(Sched.class));
                exposeBean(remoter);
                exposeBean(remoter.xioClient, "xioClient");
                ep.fire(name + ".started");
            }
        };
    }


    static ServerTpl 压测() {
        return new ServerTpl("testExec") {
            @Inject Sched sched;

            @EL(name = "sys.started", async = true)
            void test() {
                // 新增任务的速度必须是任务执行的时长的step倍, 才会新增线程
                sched.fixedDelay(Duration.ofMillis(300), () -> {
                    async(() -> {
                        Utils.http().get("http://39.104.28.131:8080/test/timeout?wait=" + (800 + (new Random().nextInt(500)))).debug().execute();
                        log.info("===== " + exec());
                    });
                });
            }
        };
    }
}
