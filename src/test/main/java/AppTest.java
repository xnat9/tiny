import cn.xnatural.app.AppContext;
import cn.xnatural.app.Inject;
import cn.xnatural.app.ServerTpl;
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
                                log.info("测试sql: " + db.single("select count(1) from test", Integer.class));
                            }
                        }
                        , sched(), web(), db(), remoter()
                        , testExec()
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
            void stop() { if (server != null) server.stop(); }
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
            Remoter remoter;
            @EL(name = "sched.started")
            void start() {
                remoter = new Remoter(app().name(), app().id(), attrs(), exec(), ep, bean(Sched.class));
                exposeBean(remoter);
                exposeBean(remoter.getAioClient(), "aioClient");
                ep.fire(name + ".started");
            }

            @EL(name = "sys.heartbeat", async = true)
            void heartbeat() {
                remoter.sync();
                remoter.getAioServer().clean();
            }

            @EL(name = "sys.stopping", async = true)
            void stop() { remoter.stop(); }
        };
    }


    static ServerTpl testExec() {
        return new ServerTpl("testExec") {
            @Inject Sched sched;

            @EL(name = "sys.started", async = true)
            void test() {
                sched.fixedDelay(Duration.ofSeconds(1), () -> {
                    async(() -> {
                        try {
                            Thread.sleep(500 * (new Random().nextInt(30) + 1));
                            log.info("===== " + exec());
                        } catch (InterruptedException e) {
                            log.error("", e);
                        }
                    });
                });
            }
        };
    }
}
