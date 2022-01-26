import cn.xnatural.app.Utils;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

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
    void toTest() {
        log.info("to Integer: " + Utils.to(1, Integer.class));
        log.info("to String: " + Utils.to("s", String.class));
    }


    @Test
    void http() throws Exception {
        Utils.http().get("http://xnatural.cn:9090/test/cus?p2=2")
                .param("p1", 1)
                .debug().execute();
        Utils.http().post("http://xnatural.cn:9090/test/cus")
                .debug().execute();
        Utils.http().post("http://xnatural.cn:9090/test/form")
                .param("p1", "p1")
                .debug().execute();
        Utils.http().post("http://xnatural.cn:9090/test/json")
                .jsonBody(new JSONObject().fluentPut("p1", 1).toString())
                .debug().execute();
        Utils.http().post("http://xnatural.cn:9090/test/upload")
                .param("version", "1.0.1")
                .param("file", new File("d:/tmp/tmp.json"))
                .debug().execute();
        Utils.http().post("http://xnatural.cn:9090/test/upload")
                .fileStream("file", "testfile.md", new FileInputStream("d:/tmp/test.md"))
                .debug().execute();
    }


    @Test
    void buildUrl() {
        Map<String, Object> params = new HashMap<>();
        params.put("p1", "111");
        params.put("p2", 222);
        System.out.println(
                Utils.buildUrl("http://xnatural.cn:9090/test", null)
        );
        System.out.println(
                Utils.buildUrl("http://xnatural.cn:9090/test", params)
        );
        System.out.println(
                Utils.buildUrl("http://xnatural.cn:9090/test?", params)
        );
        System.out.println(
                Utils.buildUrl("http://xnatural.cn:9090/test?test=", params)
        );
        System.out.println(
                Utils.buildUrl("http://xnatural.cn:9090/test?test=aaa", params)
        );
    }


    @Test
    void ioCopy() throws Exception {
        try (InputStream is = new FileInputStream("d:/tmp/1.txt"); OutputStream os = new FileOutputStream("d:/tmp/2.txt")) {
            Utils.ioCopy(is, os);
        }
    }
}

