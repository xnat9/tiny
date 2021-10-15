package cn.xnatural.app;

import cn.xnatural.app.util.Httper;
import cn.xnatural.app.util.Tailer;
import cn.xnatural.app.util.ToMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 常用工具集
 */
public class Utils {
    protected static final Logger log = LoggerFactory.getLogger(Utils.class);
    /**
     * 得到jvm进程号
     * @return pid
     */
    public static String pid() { return ManagementFactory.getRuntimeMXBean().getName().split("@")[0]; }


    /**
     * 判断系统是否为 linux 系统
     * 判断方法来源 io.netty.channel.epoll.Native#loadNativeLibrary()
     * @return true: linux
     */
    public static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase(Locale.UK).trim().startsWith("linux");
    }


    /**
     * 项目根目录下边找目录或文件
     * @param child 项目根目录下的子目录/文件
     */
    public static File baseDir(String child) {
        File p = new File(System.getProperty("user.dir"));
        if (child != null) {return new File(p, child);}
        return p;
    }


    /**
     * 本机ipv4地址
     */
    public static String ipv4() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface current = en.nextElement();
                if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue;
                Enumeration<InetAddress> addresses = current.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress()) continue;
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return null;
    }


    /**
     * sha1 加密
     * @param bs 被加密码byte[]
     * @return 加密后 byte[]
     */
    public static byte[] sha1(byte[] bs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(bs);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * md5 hex
     * @param bs 被加密的byte[]
     * @return md5加密后的字符串
     */
    public static String md5Hex(byte[] bs) {
        try {
            byte[] secretBytes = MessageDigest.getInstance("md5").digest(bs);
            String md5code = new BigInteger(1, secretBytes).toString(16);
            for (int i = 0; i < 32 - md5code.length(); i++) {
                md5code = "0" + md5code;
            }
            return md5code;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    public static final char[] CS = new char[]{'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z'};
    /**
     * 随机字符串(区分大小写)
     * @param length 长度
     * @param prefix 前缀
     * @param suffix 后缀
     * @return str
     */
    public static String random(int length, String prefix, String suffix) {
        if (length < 1) throw new IllegalArgumentException("length must le 1");
        final char[] cs = new char[length];
        Random r = new Random();
        for (int i = 0; i < cs.length; i++) {
            cs[i] = CS[r.nextInt(CS.length)];
        }
        return (prefix == null ? "" : prefix) + String.valueOf(cs) + (suffix == null ? "" : suffix);
    }

    /**
     * 随机字符串(区分大小写)
     * @param length 长度
     * @return str
     */
    public static String random(int length) { return random(length, null, null); }


    /**
     * 类型转换
     * @param v 值
     * @param type 转换的类型
     * @return 转换后的结果
     */
    public static <T> T to(Object v, Class<T> type) {
        if (type == null) return (T) v;
        if (v == null) return null;
        else if (String.class.equals(type)) return (T) v.toString();
        else if (Boolean.class.equals(type) || boolean.class.equals(type)) return (T) Boolean.valueOf(v.toString());
        else if (Short.class.equals(type) || short.class.equals(type)) return (T) Short.valueOf(v.toString());
        else if (Integer.class.equals(type) || int.class.equals(type)) return (T) Integer.valueOf(v.toString());
        else if (BigInteger.class.equals(type)) return (T) new BigInteger(v.toString());
        else if (Long.class.equals(type) || long.class.equals(type)) return (T) Long.valueOf(v.toString());
        else if (Double.class.equals(type) || double.class.equals(type)) return (T) Double.valueOf(v.toString());
        else if (Float.class.equals(type) || float.class.equals(type)) return (T) Float.valueOf(v.toString());
        else if (BigDecimal.class.equals(type)) return (T) new BigDecimal(v.toString());
        else if (URI.class.equals(type)) return (T) URI.create(v.toString());
        else if (URL.class.equals(type)) {
            try {
                return (T) URI.create(v.toString()).toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        else if (type.isEnum()) return Arrays.stream(type.getEnumConstants()).filter((o) -> v.equals(((Enum) o).name())).findFirst().orElse(null);
        return (T) v;
    }


    /**
     * 遍历所有方法并处理
     * @param clz Class
     * @param fn 函数
     */
    public static void iterateMethod(final Class clz, Consumer<Method> fn) {
        if (fn == null) return;
        Class c = clz;
        do {
            for (Method m : c.getDeclaredMethods()) fn.accept(m);
            c = c.getSuperclass();
        } while (c != null);
    }


    /**
     * 查找字段
     * @param clz Class
     * @param fn 函数
     */
    public static void iterateField(final Class clz, Consumer<Field> fn) {
        if (fn == null) return;
        Class c = clz;
        do {
            for (Field f : c.getDeclaredFields()) fn.accept(f);
            c = c.getSuperclass();
        } while (c != null);
    }


    /**
     * 构建一个 http 请求, 支持 get, post. 文件上传.
     * @return {@link Httper}
     */
    public static Httper http() { return new Httper(); }


    /**
     * 把查询参数添加到 url 后边
     * @param urlStr url
     * @param params 参数
     * @return 完整url
     */
    public static String buildUrl(String urlStr, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return urlStr;
        String queryStr = params.entrySet().stream()
                .map(e -> {
                    try {
                        return e.getKey() + "=" + URLEncoder.encode(e.getValue().toString(), "utf-8");
                    } catch (UnsupportedEncodingException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .collect(Collectors.joining("&"));
        if (urlStr.endsWith("?") || urlStr.endsWith("&")) urlStr += queryStr;
        else if (urlStr.contains("?")) urlStr += "&" + queryStr;
        else urlStr += "?" + queryStr;
        return urlStr;
    }


    /**
     * 文件内容监控器(类linux tail)
     * @return {@link Tailer}
     */
    public static Tailer tailer() {return new Tailer();}


    /**
     * 把一个bean 转换成 一个map
     * @param bean java bean
     * @return {@link ToMap}
     */
    public static <T> ToMap<T> toMapper(T bean) { return new ToMap<>(bean); }
}
