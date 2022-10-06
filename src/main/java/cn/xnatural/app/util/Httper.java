package cn.xnatural.app.util;

import cn.xnatural.app.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Consumer;

/**
 * http 请求
 */
public class Httper {
    protected static final Logger log = LoggerFactory.getLogger(Httper.class);
    protected String                      urlStr;
    protected String                      contentType;
    protected String                      method;
    protected String                      bodyStr;
    protected Map<String, Object> params;
    // 文件流:(属性名, 文件名, 文件内容)
    protected Map<String, Map<String, Object>> fileStreams;
    protected Map<String, Object>         cookies;
    protected Map<String, String>         headers;
    protected int                         connectTimeout = 5000;
    protected int                         readTimeout = 15000;
    protected int                         respCode;
    protected Consumer<HttpURLConnection> preFn;
    protected boolean                     debug;
    protected String                      tlsVersion = "TLSv1.2";
    protected Charset charset = Charset.forName("utf-8");

    public Httper get(String url) { this.urlStr = url; this.method = "GET"; return this; }
    public Httper post(String url) { this.urlStr = url; this.method = "POST"; return this; }
    public Httper put(String url) { this.urlStr = url; this.method = "PUT"; return this; }
    public Httper delete(String url) { this.urlStr = url; this.method = "DELETE"; return this; }
    /**
     *  设置 content-type
     * @param contentType application/json, multipart/form-data, application/x-www-form-urlencoded, text/plain
     */
    public Httper contentType(String contentType) { this.contentType = contentType; return this; }
    public Httper jsonBody(String jsonStr) { this.bodyStr = jsonStr; if (contentType == null) contentType = "application/json"; return this; }
    public Httper textBody(String bodyStr) { this.bodyStr = bodyStr; if (contentType == null) contentType = "text/plain"; return this; }
    public Httper readTimeout(int timeout) { this.readTimeout = timeout; return this; }
    public Httper connectTimeout(int timeout) { this.connectTimeout = timeout; return this; }
    public Httper preConnect(Consumer<HttpURLConnection> preConnect) { this.preFn = preConnect; return this; }
    public Httper debug() { this.debug = true; return this; }
    public Httper charset(String charset) { this.charset = Charset.forName(charset); return this; }
    public Httper tlsVersion(String tlsVersion) { this.tlsVersion = tlsVersion; return this; }
    /**
     * 添加参数
     * @param name 参数名
     * @param value 支持 {@link File}
     */
    public Httper param(String name, Object value) {
        if (params == null) params = new LinkedHashMap<>();
        params.put(name, value);
        if (value instanceof File) { contentType = "multipart/form-data"; }
        return this;
    }

    /**
     * 添加文件流
     * @param pName 属性名
     * @param filename 文件名
     * @param fileStream 文件流
     */
    public Httper fileStream(String pName, String filename, InputStream fileStream) {
        if (pName == null) throw new NullPointerException("pName == null");
        if (fileStream == null) throw new NullPointerException("fileStream == null");
        if (filename == null) throw new NullPointerException("filename == null");
        if (fileStreams == null) fileStreams = new LinkedHashMap<>();
        contentType = "multipart/form-data";
        Map<String, Object> entry = fileStreams.computeIfAbsent(pName, s -> new HashMap<>(2));
        entry.put("filename", filename); entry.put("fileStream", fileStream);
        return this;
    }
    public Httper header(String name, String value) {
        if (headers == null) headers = new LinkedHashMap<>(7);
        headers.put(name, value);
        return this;
    }
    public Httper cookie(String name, Object value) {
        if (cookies == null) cookies = new LinkedHashMap<>(7);
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
        Exception ex = null;
        try {
            URL url = null;
            if (urlStr == null || urlStr.isEmpty()) throw new IllegalArgumentException("url不能为空");
            if ("GET".equals(method)) url = new URL(Utils.buildUrl(urlStr, params));
            else url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection) { // 如果是https, 就忽略验证
                SSLContext sc = SSLContext.getInstance(tlsVersion); // "TLS"
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
                conn.setDoOutput(true);
                if ("multipart/form-data".equalsIgnoreCase(contentType)) {
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
                try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
                    if (("application/json".equals(contentType) || "text/plain".equals(contentType)) && bodyStr != null) {
                        os.write(bodyStr.getBytes(charset));
                    } else if (isMulti) {
                        String end = "\r\n";
                        String twoHyphens = "--";
                        if (params != null) {
                            for (Map.Entry<String, Object> entry : params.entrySet()) {
                                os.writeBytes(twoHyphens + boundary + end);
                                if (entry.getValue() instanceof File) {
                                    String s = "Content-Disposition: form-data; name=\"" +entry.getKey()+"\"; filename=\"" +((File) entry.getValue()).getName()+ "\"" + end;
                                    os.write(s.getBytes(charset)); // 这样写是为了避免中文文件名乱码
                                    os.writeBytes(end);
                                    try (FileInputStream is = new FileInputStream((File) entry.getValue())) { // copy
                                        byte[] bs = new byte[Math.min(is.available(), 4028)];
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
                        }
                        if (fileStreams != null) {
                            for (Map.Entry<String, Map<String, Object>> entry : fileStreams.entrySet()) {
                                os.writeBytes(twoHyphens + boundary + end);
                                Map<String, Object> fileInfo = entry.getValue();
                                String s = "Content-Disposition: form-data; name=\"" +entry.getKey()+"\"; filename=\"" +fileInfo.get("filename")+ "\"" + end;
                                os.write(s.getBytes(charset)); // 这样写是为了避免中文文件名乱码
                                os.writeBytes(end);
                                try (InputStream is = (InputStream) fileInfo.get("fileStream")) { // copy
                                    byte[] bs = new byte[Math.min(is.available(), 4028)];
                                    int n;
                                    while (-1 != (n = is.read(bs))) {os.write(bs, 0, n);}
                                }
                                os.writeBytes(end);
                            }
                        }

                        os.writeBytes(twoHyphens + boundary + twoHyphens + end);
                    } else if ((params != null && !params.isEmpty())) {
                        StringBuilder sb = new StringBuilder();
                        for (Map.Entry<String, Object> entry : params.entrySet()) {
                            if (entry.getValue() != null) {
                                sb.append(entry.getKey() + "=" + URLEncoder.encode(entry.getValue().toString(), charset.toString()) + "&");
                            }
                        }
                        os.write(sb.toString().getBytes(charset));
                    }
                }
            }
            // 支持 get 传body
            else if ("GET".equals(method) && bodyStr != null && !bodyStr.isEmpty()) {
                try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
                    os.write(bodyStr.getBytes(charset));
                }
            }
            // http 状态码
            respCode = conn.getResponseCode();
            // 保存cookie
            if (conn.getHeaderFields() != null) {
                conn.getHeaderFields().entrySet().stream().filter(e -> "Set-Cookie".equalsIgnoreCase(e.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .ifPresent(cs -> {
                            for (String c : cs) {
                                String[] arr = c.split(";")[0].split("=");
                                cookie(arr[0], arr[1]);
                            }
                        });
            }

            // 取结果
            StringBuilder sb = new StringBuilder();
            try (Reader reader = new InputStreamReader(conn.getInputStream(), charset)) {
                char[] buf = new char[1024];
                int len = 0;
                while((len = reader.read(buf)) != -1) {
                    sb.append(buf, 0, len);
                }
            }
            ret = sb.toString();
        }
        catch (Exception e) {
            ex = e;
        } finally {
            if (conn != null) conn.disconnect();
        }
        if (debug) {
            String logMsg = "Send http: ("+method+")" +urlStr+ ", params: " +params+ ", bodyStr: "+ bodyStr + ", result: " + ret;
            if (ex == null) {
                log.info(logMsg);
            } else {
                log.error(logMsg, ex);
            }
        }
        if (ex != null) throw new RuntimeException(ex);
        return ret;
    }
}
