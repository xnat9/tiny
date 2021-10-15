import cn.xnatural.app.util.DB;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DBTest {
    static final Logger log = LoggerFactory.getLogger(DBTest.class);

    @Test
    void getJdbcUrlTest() throws Exception {
        try (DB repo = new DB("jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root&allowPublicKeyRetrieval=true")) {
            log.info(repo.getJdbcUrl());
        }
    }


    @Test
    void rowTest() throws Exception {
        try (DB repo = new DB("jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root&allowPublicKeyRetrieval=true")) {
            log.info(repo.row("select * from test order by id desc").toString());
        }
    }


    @Test
    void rowsTest() throws Exception {
        try (DB repo = new DB("jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root&allowPublicKeyRetrieval=true")) {
            log.info(repo.rows("select * from test where id=? limit 0, ?", 2, 10).toString());
        }
    }


    @Test
    void inTest() throws Exception {
        try (DB repo = new DB("jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root&allowPublicKeyRetrieval=true")) {
            log.info(repo.rows("select * from test where id in (?, ?)", 2, 7).toString());
        }
    }


    @Test
    void singleTest() throws Exception {
        try (DB repo = new DB("jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root&allowPublicKeyRetrieval=true")) {
            Assertions.assertNotNull(repo.single("select count(1) from test", Integer.class));
        }
    }


    @Test
    void insertTest() throws Exception {
        try (DB repo = new DB("jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root&allowPublicKeyRetrieval=true")) {
            log.info("插入一条: " + repo.execute("insert into test(name, age, create_time) values(?, ?, ?)",
                    "nn" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()), new Date().getSeconds(), new Date()));
        }
    }


    @Test
    void updateTest() throws Exception {
        try (DB repo = new DB("jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root&allowPublicKeyRetrieval=true")) {
            log.info("更新数据: " + repo.execute("update test set age=? where id=?", 30, 7));
        }
    }


    @Test
    void insertWithGeneratedKeyTest() throws Exception {
        try (DB repo = new DB("jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root&allowPublicKeyRetrieval=true")) {
            Object id = repo.insertWithGeneratedKey("insert into test(name, age, create_time) values(?, ?, ?)",
                        "nn" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()), new Date().getSeconds(), new Date());
            Assertions.assertNotNull(id);
            log.info("插入一条返回主键: " + id);
        }
    }


    @Test
    void transTest() throws Exception {
        try (DB repo = new DB("jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root&allowPublicKeyRetrieval=true")) {
            Integer count = repo.single("select count(1) from test", Integer.class);
            try {
                repo.trans(() -> {
                    repo.execute("insert into test(name, age, create_time) values(?, ?, ?)",
                            "nn" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()), new Date().getSeconds(), new Date());
                    throw new RuntimeException("xxxx");
                });
            } catch (Exception ex) {}
            Assertions.assertTrue(
                    repo.single("select count(1) from test", Integer.class) > count,
                    "trans test fail"
            );
        }
    }
}
