import cn.xnatural.app.AppContext;
import cn.xnatural.app.ServerTpl;
import cn.xnatural.enet.event.EL;
import cn.xnatural.http.HttpServer;
import cn.xnatural.jpa.Repo;

import javax.inject.Named;

public class AppTest {

    public static void main(String[] args) throws Exception {
        AppContext app = new AppContext();
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
            void stop() {
                if (server != null) server.stop();
            }
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

            @EL(name = "sys.stopping", async = true)
            void stop() { if (repo != null) repo.close(); }
        });
        app.start();
    }
}
