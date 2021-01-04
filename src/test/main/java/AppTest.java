import cn.xnatural.app.AppContext;
import cn.xnatural.app.ServerTpl;
import cn.xnatural.enet.event.EL;

import javax.inject.Inject;
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
        app.addSource(new ServerTpl("server2") {
            @Named
            ServerTpl server1;

            @EL(name = "sys.starting")
            void start() {
                log.info("{}", server1.getName());
            }
        });
        app.start();
    }
}
