import cn.xnatural.app.Utils;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CopierTest {
    static final Logger log = LoggerFactory.getLogger(CopierTest.class);

    @Test
    void mapToMapTest() {
        Map<String, Object> src = new HashMap<>();
        src.put("p1", "1111");
        src.put("time", System.currentTimeMillis());
        String result = Utils.copier(
                src,
                new HashMap<>()
        )
                .addConverter("time", o -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date((long) o)))
                .mapProp( "p1", "prop1")
                .mapProp( "time", "date")
                // .showClassProp()
                .ignoreNull(true)
                .build()
                .toString();
        log.info("map to map: " + result);
    }


    @Test
    void toMapTest() {
        String result = Utils.copier(
                new Object() {
                    public String p1 = "xxx";
                    Integer p2 = 1;
                    String p3 = "p3";
                    public String p4 = "p4";
                    public long time = System.currentTimeMillis();
                    public String p5;
                    public BigInteger p6 = BigInteger.valueOf(6);

                    public Integer getP2() { return p2; }
                    private String getPri() { return "pri";}
                },
                new HashMap<>()
        )
                .addConverter("time", o -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date((long) o)))
                .addConverter("p4", o -> "pp4")
                .mapProp( "p1", "prop1")
                .add("pp", () -> "pp")
                //.showClassProp()
                .ignoreNull(true)
                .build()
                .toString();
        log.info("javabean to map: " + result);
    }


    @Test
    void toJavabeanTest() {
        Object result = Utils.copier(
                new Object() {
                    public String p1 = "xxx";
                    Integer p2 = 1;
                    String p3 = "p3";
                    public String p4 = "p4";
                    public long time = System.currentTimeMillis();
                    public String p5;
                    public BigInteger p6 = BigInteger.valueOf(6);

                    public Integer getP2() { return p2; }
                    private String getPri() { return "pri";}
                },
                new Object() {
                    public String p1;
                    private Integer p2;
                    public Date time;
                    public void setP2(Integer p2) { this.p2 = p2; }
                    public Integer getP2() { return p2; }
                    public void setR(String r) {}
                    public String getR() {return p1;}
                }
        )
                .addConverter("time", o -> new Date((long) o))
                .build();
        log.info("javabean to javabean: " + JSON.toJSONString(result));
    }
}
