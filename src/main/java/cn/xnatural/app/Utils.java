package cn.xnatural.app;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Consumer;

import static com.alibaba.fastjson.util.ThreadLocalCache.getBytes;

public class Utils {
    protected static final Logger log = LoggerFactory.getLogger(Utils.class);
    /**
     * 得到jvm进程号
     * @return pid
     */
    public static String pid() { return ManagementFactory.getRuntimeMXBean().getName().split("@")[0]; }


    /**
     * 判断系统是否为 linux 系统
     * 判断方法来源 {@link io.netty.channel.epoll.Native#loadNativeLibrary()}
     * @return true: linux
     */
    public static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase(Locale.UK).trim().startsWith("linux");
    }


    /**
     * 项目根目录下边找目录或文件
     * @param child 项目根目录下的子目录/文件
     * @return File
     */
    public static File baseDir(String child) {
        File p = new File(System.getProperty("user.dir")).getParentFile();
        if (child != null) {return new File(p, child);}
        return p;
    }


    /**
     * 本机ipv4地址
     * @return ip
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
    static void iterateField(final Class clz, Consumer<Field> fn) {
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
    public static class Httper {
        protected String                      urlStr;
        protected String                      contentType;
        protected String                      method;
        protected String                      bodyStr;
        protected Map<String, Object>         params;
        protected Map<String, Object>         cookies;
        protected Map<String, String>         headers;
        protected int                         connectTimeout = 5000;
        protected int                         readTimeout = 15000;
        protected int                         respCode;
        protected Consumer<HttpURLConnection> preFn;
        protected boolean                     debug;

        public Httper get(String url) { this.urlStr = url; this.method = "GET"; return this; }
        public Httper post(String url) { this.urlStr = url; this.method = "POST"; return this; }
        /**
         *  设置 content-type
         * @param contentType application/json, multipart/form-data, application/x-www-form-urlencoded, text/plain
         * @return
         */
        public Httper contentType(String contentType) { this.contentType = contentType; return this; }
        public Httper jsonBody(String jsonStr) { this.bodyStr = jsonStr; if (contentType == null) contentType = "application/json"; return this; }
        public Httper textBody(String bodyStr) { this.bodyStr = bodyStr; if (contentType == null) contentType = "text/plain"; return this; }
        public Httper readTimeout(int timeout) { this.readTimeout = timeout; return this; }
        public Httper connectTimeout(int timeout) { this.connectTimeout = timeout; return this; }
        public Httper preConnect(Consumer<HttpURLConnection> preConnect) { this.preFn = preConnect; return this; }
        public Httper debug() {this.debug = true; return this;}
        /**
         * 添加参数
         * @param name 参数名
         * @param value 支持 {@link File}
         * @return
         */
        public Httper param(String name, Object value) {
            if (params == null) params = new LinkedHashMap<>();
            params.put(name, value);
            return this;
        }
        public Httper header(String name, String value) {
            if (headers == null) headers = new HashMap<>(7);
            headers.put(name, value);
            return this;
        }
        public Httper cookie(String name, Object value) {
            if (cookies == null) cookies = new HashMap<>(7);
            cookies.put(name, value);
            return this;
        }
        public Map<String, Object> cookies() {return cookies;}
        public int getResponseCode() {return respCode;}

        /**
         * 执行 http 请求
         * @return http请求结果
         */
        public String execute() {
            String ret;
            HttpURLConnection conn;
            boolean isMulti = false; // 是否为 multipart/form-data 提交
            try {
                URL url = null;
                if (urlStr == null || urlStr.isEmpty()) throw new IllegalArgumentException("url不能为空");
                if ("GET".equals(method)) url = new URL(buildUrl(urlStr, params));
                else if ("POST".equals(method)) url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                if (conn instanceof HttpsURLConnection) { // 如果是https, 就忽略验证
                    SSLContext sc = SSLContext.getInstance("TLSv1.2"); // "TLS"
                    sc.init(null, new TrustManager[] {new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { }
                        @Override
                        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { }
                        @Override
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                    }}, new SecureRandom());
                    ((HttpsURLConnection) conn).setHostnameVerifier((s, sslSession) -> true);
                    ((HttpsURLConnection) conn).setSSLSocketFactory(sc.getSocketFactory());
                }
                conn.setRequestMethod(method);
                conn.setConnectTimeout(connectTimeout);
                conn.setReadTimeout(readTimeout);
                conn.setUseCaches(false);

                // header 设置
                conn.setRequestProperty("Accept", "*/*"); // 必加
                conn.setRequestProperty("Charset", "UTF-8");
                conn.setRequestProperty("Accept-Charset", "UTF-8");
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType + ";charset=UTF-8");
                // conn.setRequestProperty("Connection", "close")
                // conn.setRequestProperty("Connection", "keep-alive")
                // conn.setRequestProperty("http_user_agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.26 Safari/537.36 Core/1.63.6726.400 QQBrowser/10.2.2265.400")
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }

                String boundary = null;
                if ("POST".equals(method)) {
                    conn.setUseCaches(false);
                    conn.setDoOutput(true);
                    if ("multipart/form-data".equals(contentType) || params.entrySet().stream().anyMatch(entry -> entry.getValue() instanceof File)) {
                        boundary = "----CustomFormBoundary" + UUID.randomUUID().toString().replace("-", "");
                        contentType = "multipart/form-data;boundary=" + boundary;
                        isMulti = true;
                    }
                }

                // cookie 设置
                if (cookies != null && !cookies.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, Object> entry : cookies.entrySet()) {
                        if (entry.getValue() != null) {
                            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
                        }
                    }
                    conn.setRequestProperty("Cookie", sb.toString());
                }

                if (preFn != null) preFn.accept(conn);
                conn.connect();  // 连接

                if ("POST".equals(method)) {
                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    if (("application/json".equals(contentType) || "text/plain".equals(contentType)) && ((params != null && !params.isEmpty()) || (bodyStr != null && !bodyStr.isEmpty()))) {
                        if (bodyStr == null) os.write(JSON.toJSONString(params).getBytes());
                        else os.write(bodyStr.getBytes("utf-8"));
                        os.flush(); os.close();
                    } else if (isMulti && (params != null && !params.isEmpty())) {
                        String end = "\r\n";
                        String twoHyphens = "--";
                        for (Map.Entry<String, Object> entry : params.entrySet()) {
                            os.writeBytes(twoHyphens + boundary + end);
                            if (entry.getValue() instanceof File) {
                                String s = "Content-Disposition: form-data; name=\"" +entry.getKey()+"\"; filename=\"" +((File) entry.getValue()).getName()+ "\"" + end;
                                os.write(s.getBytes("utf-8")); // 这样写是为了避免中文文件名乱码
                                os.writeBytes(end);
                                try (FileInputStream is = new FileInputStream((File) entry.getValue())) { // copy
                                    byte[] bs = new byte[4028];
                                    int n;
                                    while (-1 != (n = is.read(bs))) {os.write(bs, 0, n);}
                                }
                            } else {
                                os.write(("Content-Disposition: form-data; name=\"" +entry.getKey()+"\"" + end).getBytes("utf-8"));
                                os.writeBytes(end);
                                os.write(entry.getValue() == null ? "".getBytes("utf-8") : entry.getValue().toString().getBytes("utf-8"));
                            }
                            os.writeBytes(end);
                        }
                        os.writeBytes(twoHyphens + boundary + twoHyphens + end);
                        os.flush(); os.close();
                    } else if ((params != null && !params.isEmpty())) {
                        StringBuilder sb = new StringBuilder();
                        for (Map.Entry<String, Object> entry : params.entrySet()) {
                            if (entry.getValue() != null) {
                                sb.append(entry.getKey() + "=" + URLEncoder.encode(entry.getValue().toString(), "utf-8") + "&");
                            }
                        }
                        os.write(sb.toString().getBytes("utf-8"));
                        os.flush(); os.close();
                    }
                }
                // http 状态码
                respCode = conn.getResponseCode();
                // 保存cookie
                for (String c : conn.getHeaderFields().get("Set-Cookie")) {
                    String[] arr = c.split(";")[0].split("=");
                    cookie(arr[0], arr[1]);
                }

                // 取结果
                ret = conn.getInputStream().getText("utf-8");
                if (200 != respCode) {
                    throw new Exception("Http error. code: ${responseCode}, url: $urlStr, resp: ${Objects.toString(ret, "")}")
                }
                if (debug) {
                    log.info("Send http: {}, params: {}, result: " + ret, urlStr, params?:bodyStr)
                }
            } finally {
                conn?.disconnect()
            }
            return ret
        }
    }


    /**
     * 把查询参数添加到 url 后边
     * @param urlStr url
     * @param params 参数
     * @return 完整url
     */
    public static String buildUrl(String urlStr, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return urlStr;
        try {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String v = entry.getValue() == null ? "" : URLEncoder.encode(entry.getValue().toString(), "utf-8");
                if (urlStr.endsWith("?")) urlStr += (entry.getKey() + "=" + v + "&");
                else if (urlStr.endsWith("&")) urlStr += (entry.getKey() + "=" + v + "&");
                else if (urlStr.contains("?")) urlStr += ("&" + entry.getKey() + "=" + v + "&");
                else urlStr += ("?" + entry.getKey() + "=" + v + "&");
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return urlStr;
    }
}
