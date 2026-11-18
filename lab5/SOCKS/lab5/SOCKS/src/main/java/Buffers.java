import java.nio.ByteBuffer;

public class Buffers {
    private Buffers() {}


    public static int writable(ByteBuffer buf) {
        return buf == null ? 0 : buf.remaining();
    }

    public static int size(ByteBuffer buf) {
        return buf == null ? 0 : buf.position();
    }

    public static ByteBuffer ensureWritable(ByteBuffer buf, int extra, int maxCapacity) {
        if (buf == null) {
            int cap = Math.min(Math.max(extra, 4096), maxCapacity);
            if (cap < extra) return null;
            return ByteBuffer.allocate(cap);
        }
        if (buf.remaining() >= extra) return buf;
        int newCap = Math.max(buf.capacity() * 2, buf.position() + extra);
        if (newCap > maxCapacity) return null;
        ByteBuffer nb = ByteBuffer.allocate(newCap);
        buf.flip();
        nb.put(buf);
        return nb;
    }

    public static ByteBuffer toReadMode(ByteBuffer buf) {
        if (buf == null) return null;
        buf.flip();
        return buf;
    }

    public static ByteBuffer toWriteMode(ByteBuffer buf) {
        if (buf == null) return null;
        buf.compact();
        return buf;
    }
}