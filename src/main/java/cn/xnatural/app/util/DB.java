package cn.xnatural.app.util;

import javax.sql.DataSource;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Date;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 数据库 sql 操作工具
 */
public class DB implements AutoCloseable {
    protected volatile DataSource dataSource;
    /**
     * 最大返回条数限制
     */
    protected Integer maxRows = 5000;
    /**
     * dataSource 属性集
     */
    protected final Map<String, Object> dsAttr = new HashMap<>();
    /**
     * 事务连接,保存在线程上下文
     */
    protected static final ThreadLocal<Connection> txConn = new ThreadLocal<>();

    /**
     * 创建一个 {@link DB}
     * @param dataSource 外部数据源
     */
    public DB(DataSource dataSource) {
        if (dataSource == null) throw new IllegalArgumentException("Param dataSource required");
        this.dataSource = dataSource;
    }

    /**
     * 创建一个 {@link DB}
     * @param dsAttr 属性集
     */
    public DB(Map<String, Object> dsAttr) {
        if (dsAttr == null) throw new IllegalArgumentException("Param dsAttr required");
        this.dsAttr.putAll(dsAttr);
    }

    /**
     * 创建一个{@link DB}
     * @param jdbcUrl jdbc 连接地址
     */
    public DB(String jdbcUrl) { this(jdbcUrl, null, null, 1, 8); }

    /**
     * 创建一个{@link DB}
     * @param jdbcUrl jdbc 连接地址
     * @param username 连接用户名
     * @param password 连接密码
     */
    public DB(String jdbcUrl, String username, String password) { this(jdbcUrl, username, password, null, null); }

    /**
     * 创建一个{@link DB}
     * @param jdbcUrl jdbc 连接地址
     * @param minIdle 最小连接
     * @param maxActive 最大活动连接
     */
    public DB(String jdbcUrl, Integer minIdle, Integer maxActive) { this(jdbcUrl, null, null, minIdle, maxActive); }

    /**
     * 创建一个{@link DB}
     * @param jdbcUrl jdbc 连接地址
     * @param username 连接用户名
     * @param password 连接密码
     * @param minIdle 最小连接
     * @param maxActive 最大活动连接
     */
    public DB(String jdbcUrl, String username, String password, Integer minIdle, Integer maxActive) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) throw new IllegalArgumentException("Param jdbcUrl required");
        if (minIdle == null || minIdle < 0) throw new IllegalArgumentException("Param minIdle must >= 0");
        if (maxActive == null || maxActive <= 0) throw new IllegalArgumentException("Param maxActive must > 0");
        dsAttr.put("url", jdbcUrl); dsAttr.put("jdbcUrl", jdbcUrl);
        if (username != null) dsAttr.put("username", username);
        if (password != null) dsAttr.put("password", password);
        dsAttr.put("minIdle", minIdle); dsAttr.put("minimumIdle", minIdle);
        dsAttr.put("maxActive", maxActive); dsAttr.put("maximumPoolSize", maxActive);
    }



    /**
     * 设置 {@link DataSource} 属性
     * @param attrName 属性名
     * @param attrValue 属性值
     * @return {@link DB}
     */
    public DB dsAttr(String attrName, Object attrValue) {
        if (dataSource != null) throw new RuntimeException("dataSource already created");
        dsAttr.put(attrName, attrValue);
        return this;
    }


    /**
     * 设置限制最大返回条数
     * @param maxRows > 0
     * @return {@link DB}
     */
    public DB setMaxRows(int maxRows) {
        if (maxRows < 1) throw new IllegalArgumentException("Param maxRows must > 0");
        this.maxRows = maxRows;
        return this;
    }


    /**
     * 执行连接
     * @param fn 函数
     * @return {@link DB}
     */
    public <T> T withConn(Function<Connection, T> fn) {
        init();
        Connection conn = null;
        try {
            conn = txConn.get() == null ? dataSource.getConnection() : txConn.get();
            return fn.apply(conn);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (txConn.get() == null) { // 证明当前线程没有事务, 需要直接释放连接
                try {
                    conn.close();
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }


    /**
     * 开启一个事务
     * @param fn 事务执行函数
     * @param <T> 返回类型
     * @return 返回值
     */
    public <T> T trans(Supplier<T> fn) {
        return withConn(conn -> {
            try {
                boolean ac = conn.getAutoCommit();
                try {
                    txConn.set(conn); conn.setAutoCommit(false);
                    T t = fn.get(); conn.commit();
                    return t;
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    txConn.set(null);
                    conn.setAutoCommit(ac);
                    conn.close();
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }


    /**
     * 插入一条数据/更新数据
     * @param sql sql 语句
     * @param params 参数
     * @return 成功条数
     */
    public int execute(String sql, Object...params) {
        return withConn(conn -> {
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                fillParam(pst, params);
                return pst.executeUpdate();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }


    /**
     * 执行一个存储过程
     * @param sql sql语句
     * @param params 参数
     * @return 影响条数
     */
    public int call(String sql, Object...params) {
        return withConn(conn -> {
            try (CallableStatement cst = conn.prepareCall(sql)) {
                fillParam(cst, params);
                return cst.executeUpdate();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }


    /**
     * 插入一条数据 并 返回第一个 数据库自生成字段
     * @param sql sql 语句
     * @param params 参数
     * @return 自生成字段的值
     */
    public Object insertWithGeneratedKey(String sql, Object...params) {
        return withConn(conn -> {
            try (PreparedStatement pst = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                fillParam(pst, params);
                pst.executeUpdate();
                try (ResultSet rs = pst.getGeneratedKeys()) {
                    if (rs.next()) return rs.getObject(1);
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }


    /**
     * 查询多条数据
     * @param sql sql 语句
     * @param params 参数
     * @return 多条数据结果
     */
    public List<Map<String, Object>> rows(String sql, Object...params) {
        final List<Map<String, Object>> result = new LinkedList<>();
        return withConn(conn -> {
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                fillParam(pst, params);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        ResultSetMetaData metadata = rs.getMetaData();
                        Map<String, Object> row = new LinkedHashMap<>(metadata.getColumnCount(), 1);
                        result.add(row);
                        for (int i = 1; i <= metadata.getColumnCount(); i++) {
                            row.put(metadata.getColumnLabel(i), rs.getObject(i));
                        }
                    }
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return result;
        });
    }


    /**
     * 返回一条数据
     * @param sql sql语句
     * @param params 参数
     * @return 一条数据
     */
    public Map<String, Object> row(String sql, Object...params) {
        final Map<String, Object> result = new LinkedHashMap<>();
        return withConn(conn -> {
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                fillParam(pst, params);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) {
                        ResultSetMetaData metadata = rs.getMetaData();
                        for (int i = 1; i <= metadata.getColumnCount(); i++) {
                            result.put(metadata.getColumnLabel(i), rs.getObject(i));
                        }
                    }
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return result;
        });
    }


    /**
     * 查询单个值
     * @param sql sql 语句
     * @param retType 返回的类型
     *                Integer.class, Long.class, String.class, Double.class, BigDecimal.class, Boolean.class, Date.class
     * @param params 参数
     * @param <T> 类型
     * @return 单个值
     */
    public  <T> T single(String sql, Class<T> retType, Object...params) {
        return (T) withConn(conn -> {
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                fillParam(pst, params);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) {
                        if (Integer.class.equals(retType)) return rs.getInt(1);
                        if (Long.class.equals(retType)) return rs.getLong(1);
                        if (Double.class.equals(retType)) return rs.getDouble(1);
                        if (BigDecimal.class.equals(retType)) return rs.getBigDecimal(1);
                        if (Boolean.class.equals(retType)) return rs.getBoolean(1);
                        if (Date.class.equals(retType)) return rs.getDate(1);
                        if (String.class.equals(retType)) return rs.getString(1);
                        return rs.getObject(1);
                    }
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }


    protected void fillParam(PreparedStatement pst, Object...params) throws SQLException {
        ParameterMetaData metaData = pst.getParameterMetaData();
        if (metaData != null && params != null) {
            for (int i = 0; i < params.length; i++) {
                pst.setObject(i + 1, params[i]);
            }
        }
        pst.setMaxRows(maxRows);
    }


    protected DB init() {
        if (dataSource == null) {
            synchronized (this) {
                if (dataSource == null) {
                    dataSource = createDataSource(dsAttr);
                }
            }
        }
        return this;
    }


    @Override
    public void close() throws Exception {
        try {
            dataSource.getClass().getMethod("close").invoke(dataSource);
        } catch (Exception e) {}
    }


    /**
     * 获取 jdbc 连接地址
     */
    public String getJdbcUrl() {
        if (dataSource == null) init();
        try {
            Class c = dataSource.getClass();
            do {
                for (Field f : c.getDeclaredFields()) {
                    if ("jdbcUrl".equals(f.getName()) || "url".equals(f.getName())) {
                        f.setAccessible(true);
                        return (String) f.get(dataSource);
                    }
                }
                c = c.getSuperclass();
            } while (c != null);
        } catch (Exception ex) {}
        return null;
    }


    /**
     * 创建一个 数据源
     * @param dsAttr 连接池属性
     * @return {@link DataSource} 数据源
     */
    public static DataSource createDataSource(Map<String, Object> dsAttr) {
        DataSource ds = null;
        // druid 数据源
        try {
            Map props = new HashMap();
            dsAttr.forEach((s, o) -> props.put(s, Objects.toString(o, "")));
            // if (!props.containsKey("validationQuery")) props.put("validationQuery", "select 1") // oracle 不行
            if (!props.containsKey("filters")) { // 默认监控慢sql
                props.put("filters", "stat");
            }
            if (!props.containsKey("connectionProperties")) {
                // com.alibaba.druid.filter.stat.StatFilter
                props.put("connectionProperties", "druid.stat.logSlowSql=true;druid.stat.slowSqlMillis=5000");
            }
            ds = (DataSource) Class.forName("com.alibaba.druid.pool.DruidDataSourceFactory").getMethod("createDataSource", Map.class).invoke(null, props);
        }
        catch(ClassNotFoundException ex) {}
        catch(Exception ex) { throw new RuntimeException(ex); }
        if (ds != null) return ds;

        // Hikari 数据源
        try {
            Class<?> clz = Class.forName("com.zaxxer.hikari.HikariDataSource");
            ds = (DataSource) clz.newInstance();
            for (PropertyDescriptor pd : Introspector.getBeanInfo(clz).getPropertyDescriptors()) {
                Object v = dsAttr.get(pd.getName());
                if (v != null) {
                    if (Integer.class.equals(pd.getPropertyType()) || int.class.equals(pd.getPropertyType())) pd.getWriteMethod().invoke(ds, Integer.valueOf(v.toString()));
                    else if (Long.class.equals(pd.getPropertyType()) || long.class.equals(pd.getPropertyType())) pd.getWriteMethod().invoke(ds, Long.valueOf(v.toString()));
                    else if (Boolean.class.equals(pd.getPropertyType()) || boolean.class.equals(pd.getPropertyType())) pd.getWriteMethod().invoke(ds, Boolean.valueOf(v.toString()));
                    else pd.getWriteMethod().invoke(ds, v);
                }
            }
        }
        catch(ClassNotFoundException ex) {}
        catch(Exception ex) { throw new RuntimeException(ex); }
        if (ds != null) return ds;

        // dbcp2 数据源
        try {
            Properties props = new Properties();
            dsAttr.forEach((s, o) -> props.put(s, Objects.toString(o, "")));
            // if (!props.containsKey("validationQuery")) props.put("validationQuery", "select 1");
            ds = (DataSource) Class.forName("org.apache.commons.dbcp2.BasicDataSourceFactory").getMethod("createDataSource", Properties.class).invoke(null, props);
        }
        catch(ClassNotFoundException ex) {}
        catch(Exception ex) { throw new RuntimeException(ex); }

        if (ds == null) throw new RuntimeException("No found DataSource impl class");
        return ds;
    }
}
