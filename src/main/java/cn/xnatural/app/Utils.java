package cn.xnatural.app;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

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
     * @return File
     */
    public static File baseDir(String child) {
        File p = new File(System.getProperty("user.dir"));
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
     * http 请求
     */
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
        protected Charset charset = Charset.forName("utf-8");

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
        public Httper charset(String charset) {this.charset = Charset.forName(charset); return this;}
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
            String ret = null;
            HttpURLConnection conn = null;
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
                conn.setRequestProperty("Charset", charset.toString());
                conn.setRequestProperty("Accept-Charset", charset.toString());
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType + ";charset=" + charset);
                // conn.setRequestProperty("Connection", "close")
                // conn.setRequestProperty("Connection", "keep-alive")
                // conn.setRequestProperty("http_user_agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.26 Safari/537.36 Core/1.63.6726.400 QQBrowser/10.2.2265.400")
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }

                String boundary = null;
                if ("POST".equals(method)) {
                    conn.setUseCaches(false);
                    conn.setDoOutput(true);
                    if ("multipart/form-data".equals(contentType) || (params != null && params.entrySet().stream().anyMatch(entry -> entry.getValue() instanceof File))) {
                        isMulti = true;
                        boundary = "----CustomFormBoundary" + UUID.randomUUID().toString().replace("-", "");
                        contentType = "multipart/form-data;boundary=" + boundary;
                        conn.setRequestProperty("Content-Type", contentType + ";charset=" + charset);
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
                        else os.write(bodyStr.getBytes(charset));
                        os.flush(); os.close();
                    } else if (isMulti && (params != null && !params.isEmpty())) {
                        String end = "\r\n";
                        String twoHyphens = "--";
                        for (Map.Entry<String, Object> entry : params.entrySet()) {
                            os.writeBytes(twoHyphens + boundary + end);
                            if (entry.getValue() instanceof File) {
                                String s = "Content-Disposition: form-data; name=\"" +entry.getKey()+"\"; filename=\"" +((File) entry.getValue()).getName()+ "\"" + end;
                                os.write(s.getBytes(charset)); // 这样写是为了避免中文文件名乱码
                                os.writeBytes(end);
                                try (FileInputStream is = new FileInputStream((File) entry.getValue())) { // copy
                                    byte[] bs = new byte[4028];
                                    int n;
                                    while (-1 != (n = is.read(bs))) {os.write(bs, 0, n);}
                                }
                            } else {
                                os.write(("Content-Disposition: form-data; name=\"" +entry.getKey()+"\"" + end).getBytes(charset));
                                os.writeBytes(end);
                                os.write(entry.getValue() == null ? "".getBytes(charset) : entry.getValue().toString().getBytes(charset));
                            }
                            os.writeBytes(end);
                        }
                        os.writeBytes(twoHyphens + boundary + twoHyphens + end);
                        os.flush(); os.close();
                    } else if ((params != null && !params.isEmpty())) {
                        StringBuilder sb = new StringBuilder();
                        for (Map.Entry<String, Object> entry : params.entrySet()) {
                            if (entry.getValue() != null) {
                                sb.append(entry.getKey() + "=" + URLEncoder.encode(entry.getValue().toString(), charset.toString()) + "&");
                            }
                        }
                        os.write(sb.toString().getBytes(charset));
                        os.flush(); os.close();
                    }
                }
                // http 状态码
                respCode = conn.getResponseCode();
                // 保存cookie
                if (conn.getHeaderFields() != null) {
                    List<String> cs = conn.getHeaderFields().get("Set-Cookie");
                    if (cs == null) cs = conn.getHeaderFields().get("set-cookie");
                    if (cs != null) {
                        for (String c : cs) {
                            String[] arr = c.split(";")[0].split("=");
                            cookie(arr[0], arr[1]);
                        }
                    }
                }

                // 取结果
                StringBuilder sb = new StringBuilder();
                try (Reader reader = new InputStreamReader(conn.getInputStream(), charset)) {
                    char[] buf = new char[1024];
                    int length = 0;
                    while((length = reader.read(buf)) != -1) {
                        sb.append(buf, 0, length);
                    }
                }
                ret = sb.toString();
                if (200 != respCode) {
                    throw new RuntimeException("Http error. code: " +respCode+ ", url: " +urlStr+ ", resp: " + ret);
                }
                if (debug) {
                    log.info("Send http: {}, params: {}, result: " + ret, urlStr, params == null ? bodyStr : params);
                }
            } catch (Exception ex) {
                log.error("Http error. " + urlStr+ ", params: " +(params == null ? bodyStr : params)+ ", result: " + ret, ex);
            } finally {
                if (conn != null) conn.disconnect();
            }
            return ret;
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


    /**
     * 文件内容监控器(类linux tail)
     * @return {@link Tailer}
     */
    public static Tailer tailer() {return new Tailer();}

    /**
     * 文件内容监控器(类linux tail)
     */
    public static class Tailer {
        private Thread                    th;
        private boolean                   stopFlag;
        private Function<String, Boolean> lineFn;
        private Executor exec;

        /**
         * 处理输出行
         * @param lineFn 函数返回true 继续输出, 返回false则停止输出
         */
        public Tailer handle(Function<String, Boolean> lineFn) {this.lineFn = lineFn; return this;}
        /**
         * 设置处理线程池
         * @param exec 线程池
         */
        public Tailer exec(Executor exec) {this.exec = exec; return this;}
        /**
         * 停止
         */
        public void stop() {this.stopFlag = true;}

        /**
         * tail 文件内容监控
         * @param file 文件全路径
         * @return {@link Tailer}
         */
        public Tailer tail(String file) { return tail(file, 5); }
        /**
         * tail 文件内容监控
         * @param file 文件全路径
         * @param follow 从最后第几行开始
         * @return {@link Tailer}
         */
        public Tailer tail(String file, Integer follow) {
            if (lineFn == null) lineFn = (line) -> {System.out.println(line); return true;};
            Runnable fn = () -> {
                String tName = Thread.currentThread().getName();
                try {
                    Thread.currentThread().setName("Tailer-" + file);
                    run(file, (follow == null ? 0 : follow));
                } catch (Exception ex) {
                    log.error("Tail file " + file + " error", ex);
                } finally {
                    Thread.currentThread().setName(tName);
                }
            };
            if (exec != null) {
                exec.execute(fn);
            } else {
                th = new Thread(fn, "Tailer-" + file);
                // th.setDaemon(true);
                th.start();
            }
            return this;
        }

        private void run(String file, Integer follow) throws Exception {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                Queue<String> buffer = (follow != null && follow > 0) ? new LinkedList<>() : null; // 用来保存最后几行(follow)数据

                // 当前第一次到达文件结尾时,设为true
                boolean firstEnd = false;
                String line;
                while (!stopFlag) {
                    line = raf.readLine();
                    if (line == null) { // 当读到文件结尾时(line为空即为文件结尾)
                        if (firstEnd) {
                            Thread.sleep(100L * new Random().nextInt(10));
                            // raf.seek(file.length()) // 重启定位到文件结尾(有可能文件被重新写入了)
                            continue;
                        }
                        firstEnd = true;
                        if (buffer != null) { // 第一次到达结尾后, 清理buffer数据
                            do {
                                line = buffer.poll();
                                if (line == null) break;
                                this.stopFlag = !lineFn.apply(line);
                            } while (!stopFlag);
                            buffer = null;
                        }
                    } else { // 读到行有数据
                        line = new String(line.getBytes("ISO-8859-1"),"utf-8");
                        if (firstEnd) { // 直接处理行字符串
                            stopFlag = !lineFn.apply(line);
                        } else if (follow != null && follow > 0) {
                            buffer.offer(line);
                            if (buffer.size() > follow) buffer.poll();
                        }
                    }
                }
            }
        }
    }


    /**
     * 把一个bean 转换成 一个map
     * @param bean java bean
     * @return {@link ToMap}
     */
    public static <T> ToMap toMapper(T bean) { return new ToMap<>(bean); }

    /**
     * 转换成Map
     * @param <T>
     */
    public static class ToMap<T> {
        private T                     bean;
        private Map<String, String>   propAlias;
        private final Set<String> ignore = new HashSet<>(Arrays.asList("class"));
        private Map<String, Function> valueConverter;
        private Map<String, Map<String, Function>> newProp;
        private boolean               ignoreNull = false;// 默认不忽略空值属性
        private Comparator<String>    comparator;
        private Map<String, Object> result; //结果map
        private Map<String, Object> attrs; //手动添加的属性

        public ToMap(T bean) { this.bean = bean; }
        public ToMap<T> aliasProp(String originPropName, String aliasName) {
            if (originPropName == null || originPropName.isEmpty() || originPropName.trim().isEmpty()) throw new IllegalArgumentException("Param originPropName not empty");
            if (aliasName == null || aliasName.isEmpty() || aliasName.trim().isEmpty()) throw new IllegalArgumentException("Param aliasName not empty");
            if (propAlias == null) propAlias = new HashMap<>(7);
            propAlias.put(originPropName, aliasName.trim());
            return this;
        }
        public ToMap<T> showClassProp() { ignore.remove("class"); return this; }
        public ToMap<T> ignoreNull(boolean ignoreNull) { this.ignoreNull = ignoreNull; return this; }
        public ToMap<T> ignoreNull() { this.ignoreNull = true; return this; }
        public ToMap<T> sort(Comparator<String> comparator) { this.comparator = comparator; return this; }
        public ToMap<T> sort() { this.comparator = Comparator.naturalOrder(); return this; }
        public ToMap<T> ignore(String... propNames) {
            if (propNames == null) return this;
            Collections.addAll(ignore, propNames);
            return this;
        }

        /**
         * 值转换
         * @param propName 属性名
         * @param converter 值转换器
         * @return {@link ToMap}
         */
        public ToMap<T> addConverter(String propName, Function converter) {
            if (propName == null || propName.isEmpty() || propName.trim().isEmpty()) throw new IllegalArgumentException("Param propName not empty");
            if (converter == null) throw new IllegalArgumentException("Param converter required");
            if (valueConverter == null) valueConverter = new HashMap<>(7);
            valueConverter.put(propName, converter);
            return this;
        }

        /**
         * 值转换
         * @param originPropName 原属性名
         * @param newPropName 新属性名
         * @param converter 值转换器
         * @return {@link ToMap}
         */
        public ToMap<T> addConverter(String originPropName, String newPropName, Function converter) {
            if (originPropName == null || originPropName.isEmpty() || originPropName.trim().isEmpty()) throw new IllegalArgumentException("Param originPropName not empty");
            if (newPropName == null || newPropName.isEmpty() || newPropName.trim().isEmpty()) throw new IllegalArgumentException("Param newPropName not empty");
            if (converter == null) throw new IllegalArgumentException("Param converter required");
            if (newProp == null) { newProp = new HashMap<>(); }
            newProp.computeIfAbsent(originPropName, s -> new HashMap<>(7)).put(newPropName, converter);
            return this;
        }

        /**
         * 手动添加属性
         * @param pName 属性名
         * @param value 属性值
         * @return {@link ToMap}
         */
        public ToMap<T> add(String pName, Object value) {
            if (attrs == null) attrs = new HashMap<>();
            attrs.put(pName, value);
            return this;
        }


        /**
         * 填充属性
         * @param pName 属性名
         * @param originValue 属性原始值
         */
        protected void fill(String pName, Object originValue) {
            if (result == null) throw new IllegalArgumentException("Please after build");
            if (propAlias != null && propAlias.get(pName) != null) pName = propAlias.get(pName);
            if (ignore.contains(pName)) return;
            if (valueConverter != null && valueConverter.containsKey(pName)) {
                Object v = valueConverter.get(pName).apply(originValue);
                if (!(v == null && ignoreNull)) {result.put(pName, v);}
            } else if (newProp != null && newProp.get(pName) != null) {
                for (Iterator<Map.Entry<String, Function>> it = newProp.get(pName).entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, Function> e = it.next();
                    Object v = e.getValue().apply(originValue);
                    if (!(v == null && ignoreNull)) {result.put(e.getKey(), v);}
                }
            } else if (!(originValue == null && ignoreNull)) result.put(pName, originValue);
        }


        /**
         * 开始构建 结果Map
         * @return 结果Map
         */
        public Map<String, Object> build() {
            result = comparator != null ? new TreeMap<>(comparator) : new LinkedHashMap();
            if (bean == null) return result;
            iterateMethod(bean.getClass(), method -> { // 遍历getter属性
                try {
                    if (!void.class.equals(method.getReturnType()) && method.getName().startsWith("get") && method.getParameterCount() == 0 && !"getMetaClass".equals(method.getName())) { // 属性
                        String tmp = method.getName().replace("get", "");
                        method.setAccessible(true);
                        fill(Character.toLowerCase(tmp.charAt(0)) + tmp.substring(1), method.invoke(bean));
                    }
                } catch (Exception e) { /** ignore **/ }
            });

            iterateField(bean.getClass(), field -> { // 遍历字段属性
                if (!Modifier.isPublic(field.getModifiers())) return;
                if (result.containsKey(field.getName())) return;
                try {
                    field.setAccessible(true);
                    fill(field.getName(), field.get(bean));
                } catch (Exception e) { /** ignore **/ }
            });
            if (attrs != null) attrs.forEach((s, o) -> fill(s, o));
            return result;
        }
    }
}
