import cn.xnatural.app.AppContext;
import cn.xnatural.app.ServerTpl;
import cn.xnatural.enet.event.EL;
import cn.xnatural.http.HttpServer;
import cn.xnatural.jpa.Repo;
import cn.xnatural.remoter.Remoter;
import cn.xnatural.sched.Sched;
import org.junit.jupiter.api.Test;

import javax.inject.Named;
import java.time.Duration;

public class AppTest {

    @Test
    void appTest() throws Exception {
        final AppContext app = new AppContext();
        app.addSource(new ServerTpl("server1") {
            @EL(name = "sys.starting")
            void start() {
                log.info("{} start", name);
            }
        });
        app.addSource(new ServerTpl() {
            @Named
            ServerTpl server1;

            @EL(name = "sys.starting")
            void start() {
                log.info("{} ========= {}", name, server1.getName());
            }
        });
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
            void stop() { if (server != null) server.stop(); }
        });
        app.addSource(new ServerTpl("jpa_local") { //数据库 jpa_local
            Repo repo;
            @EL(name = "sys.starting", async = true)
            void start() {
                repo = new Repo(attrs()).init();
                exposeBean(repo);
                log.info(repo.firstRow("select count(1) as total from test").get("total").toString());
                ep.fire(name + ".started");
            }

            @EL(name = "sys.stopping", async = true, order = 2f)
            void stop() { if (repo != null) repo.close(); }
        });
        app.addSource(new ServerTpl("sched") { // 定时任务
            Sched sched;
            @EL(name = "sys.starting", async = true)
            void start() {
                sched = new Sched(attrs(), exec()).init();
                exposeBean(sched);
                ep.fire(name + ".started");
            }
            @EL(name = "sched.after")
            void after(Duration duration, Runnable fn) {sched.after(duration, fn);}

            void stop() { if (sched != null) sched.stop(); }
        });
        app.addSource(new ServerTpl("remoter") {
            Remoter remoter;
            @EL(name = "sched.started")
            void start() {
                remoter = new Remoter(app.name(), app.id(), attrs(), exec(), ep, bean(Sched.class));
                exposeBean(remoter);
                exposeBean(remoter.getAioClient());
                ep.fire(name + ".started");
            }

            @EL(name = "sys.heartbeat", async = true)
            void heartbeat() {
                remoter.sync();
                remoter.getAioServer().clean();
            }
            @EL(name = "sys.stopping", async = true)
            void stop() { remoter.stop(); }
        });
        app.start();
    }


    @Test
    void sysLoadTest() throws Exception {
        final AppContext app = new AppContext();
        app.start();
        for (int i = 0; i < 100000; i++) {
            int finalI = i;
            app.exec().execute(() -> {
                System.out.println("Task " + finalI);
            });
        }
        Thread.sleep(10 * 60 * 1000);
    }
}
