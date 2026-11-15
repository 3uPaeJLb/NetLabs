import java.nio.ByteBuffer;

public class Buffers {
    private Buffers() {}

    public static ByteBuffer ensureWritable(ByteBuffer buf, int extra) {
        if (buf == null) return ByteBuffer.allocate(Math.max(extra, 4096));
        if (buf.remaining() >= extra) return buf;
        int newCap = Math.max(buf.capacity() * 2, buf.position() + extra);
        ByteBuffer nb = ByteBuffer.allocate(newCap);
        buf.flip();
        nb.put(buf);
        return nb;
    }

    public static int size(ByteBuffer buf) { return buf == null ? 0 : buf.position(); }

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