package cn.xnatural.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * 汇聚流: 顺序汇聚 多个流到一个流 直到 结束
 * 流1    |
 * 流2    | ==> 汇聚流 ==> 读取
 * 流3    |
 * NOTE: {@link #addStream(InputStream)} 和 {@link #read()} 不能是同一个线程
 */
public class ConvergeInputStream extends InputStream {
    /**
     * 已读长度
     */
    protected long readCount = 0;
    /**
     * 总每度
     */
    protected final long length;
    /**
     * 对列流
     */
    protected final Queue<InputStream> streamQueue = new ConcurrentLinkedQueue<>();
    /**
     * 当前正在读取的流
     */
    protected InputStream currentStream;


    public ConvergeInputStream(long length) {
        if (length < 0) throw new IllegalArgumentException("Param length > 0");
        this.length = length;
    }


    @Override
    public int read() throws IOException {
        if (isEnd()) {
            streamQueue.clear();
            return -1;
        }
        if (currentStream == null && streamQueue.isEmpty()) { // 暂停等待新的流加进来
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (currentStream == null) currentStream = streamQueue.poll();
        int result =  currentStream.read();
        if (result == -1) {
            currentStream = null;
            return read();
        }
        readCount++;
        return result;
    }


    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (currentStream != null && currentStream.available() >= len) {
            readCount += len;
            return currentStream.read(b, off, len);
        }
        return super.read(b, off, len);
    }


    /**
     * 添加新的流
     */
    public ConvergeInputStream addStream(InputStream stream) {
        if (stream == null) throw new NullPointerException("Param stream null");
        if (isEnd()) throw new RuntimeException("Already end");
        streamQueue.offer(stream);
        synchronized (this) {
            notify();
        }
        return this;
    }


    @Override
    public int available() throws IOException { return (int) (length - readCount); }


    /**
     * 是否计取完成
     */
    public boolean isEnd() { return readCount >= length; }


    /**
     * 剩余多少还没处理的流
     */
    public int left() { return streamQueue.size(); }
}
