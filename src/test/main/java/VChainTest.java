import cn.xnatural.app.v.VChain;
import cn.xnatural.app.v.VProcessor;
import org.junit.jupiter.api.Test;

public class VChainTest {


    @Test
    void testVChain() {
        new VChain().add(new VProcessor() {
            @Override
            public Object pre(Object input) {
                return null;
            }

            @Override
            public Object post(Object input) {
                return null;
            }
        }).add(new VProcessor() {
            @Override
            public Object pre(Object input) {
                return null;
            }

            @Override
            public Object post(Object input) {
                return null;
            }
        }).run("xx");
    }
}
