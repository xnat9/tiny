package cn.xnatural.app.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * 文件内容监控器(类linux tail)
 */
public class Tailer {
    protected static final Logger log = LoggerFactory.getLogger(Tailer.class);
    private Thread                    th;
    private boolean                   stopFlag;
    private Function<String, Boolean> lineFn;
    private Executor                  exec;

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
