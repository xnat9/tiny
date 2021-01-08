import cn.xnatural.app.Utils;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UtilsTest {
    static final Logger log = LoggerFactory.getLogger(UtilsTest.class);


    public static void main(String[] args) {
        Utils.tailer().tail("/media/xnat/store/code_repo/gy/log/app.log", 10);
    }


    @Test
    void pid() {
        log.info(Utils.pid());
    }


    @Test
    void isLinux() {
        log.info(Utils.isLinux() + "");
    }


    @Test
    void baseDir() {
        log.info(Utils.baseDir(null).getAbsolutePath());
    }


    @Test
    void ipv4() {
        log.info(Utils.ipv4());
    }


    @Test
    void md5Hex() {
        log.info(Utils.md5Hex("000".getBytes(Charset.forName("utf-8"))));
    }


    @Test
    void randomStr() {
        log.info(Utils.random(20, "app_", null));
    }



    @Test
    void http() {
        Utils.http().get("http://xnatural.cn:9090/test/cus?p2=2").param("p1", 1).debug().execute();
        Utils.http().post("http://xnatural.cn:9090/test/cus").debug().execute();
        Utils.http().post("http://xnatural.cn:9090/test/form").param("p1", "p1").debug().execute();
        Utils.http().post("http://xnatural.cn:9090/test/json").jsonBody(new JSONObject().fluentPut("p1", 1).toString()).debug().execute();
        Utils.http().post("http://xnatural.cn:9090/test/upload").debug()
//        Utils.http().post("http://localhost:7070/test/upload").debug()
                .param("version", "1.0.1")
                .param("file", new File("/home/xnat/Downloads/xxb.doc"))
                .execute();
    }


    @Test
    void toMap() {
        log.info(
                Utils.toMapper(new Object() {
                    public String p1 = "xxx";
                    Integer p2 = 1;
                    String p3 = "p3";
                    public String p4 = "p4";
                    public long time = System.currentTimeMillis();
                    public String p5;
                    public BigInteger p6 = BigInteger.valueOf(6);

                    public Integer getP2() { return p2; }
                }).addConverter("time", o -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date((long) o)))
                        .addConverter("p4", "pp4", o -> "pp4")
                        .aliasProp("p1", "prop1")
                        .add("pp", "pp")
                        // .showClassProp()
                        .ignoreNull()
                        .build().toString()
        );
    }
}
