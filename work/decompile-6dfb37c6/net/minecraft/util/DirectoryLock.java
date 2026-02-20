package net.minecraft.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DirectoryLock implements AutoCloseable {

    public static final String LOCK_FILE = "session.lock";
    private final FileChannel lockFile;
    private final FileLock lock;
    private static final ByteBuffer DUMMY;

    public static DirectoryLock create(Path dir) throws IOException {
        Path path1 = dir.resolve("session.lock");

        FileUtil.createDirectoriesSafe(dir);
        FileChannel filechannel = FileChannel.open(path1, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        try {
            filechannel.write(DirectoryLock.DUMMY.duplicate());
            filechannel.force(true);
            FileLock filelock = filechannel.tryLock();

            if (filelock == null) {
                throw DirectoryLock.LockException.alreadyLocked(path1);
            } else {
                return new DirectoryLock(filechannel, filelock);
            }
        } catch (IOException ioexception) {
            try {
                filechannel.close();
            } catch (IOException ioexception1) {
                ioexception.addSuppressed(ioexception1);
            }

            throw ioexception;
        }
    }

    private DirectoryLock(FileChannel lockFile, FileLock lock) {
        this.lockFile = lockFile;
        this.lock = lock;
    }

    public void close() throws IOException {
        try {
            if (this.lock.isValid()) {
                this.lock.release();
            }
        } finally {
            if (this.lockFile.isOpen()) {
                this.lockFile.close();
            }

        }

    }

    public boolean isValid() {
        return this.lock.isValid();
    }

    public static boolean isLocked(Path dir) throws IOException {
        Path path1 = dir.resolve("session.lock");

        try {
            boolean flag;

            try (FileChannel filechannel = FileChannel.open(path1, StandardOpenOption.WRITE); FileLock filelock = filechannel.tryLock();) {
                flag = filelock == null;
            }

            return flag;
        } catch (AccessDeniedException accessdeniedexception) {
            return true;
        } catch (NoSuchFileException nosuchfileexception) {
            return false;
        }
    }

    static {
        byte[] abyte = "\u2603".getBytes(StandardCharsets.UTF_8);

        DUMMY = ByteBuffer.allocateDirect(abyte.length);
        DirectoryLock.DUMMY.put(abyte);
        DirectoryLock.DUMMY.flip();
    }

    public static class LockException extends IOException {

        private LockException(Path path, String message) {
            String s1 = String.valueOf(path.toAbsolutePath());

            super(s1 + ": " + message);
        }

        public static DirectoryLock.LockException alreadyLocked(Path path) {
            return new DirectoryLock.LockException(path, "already locked (possibly by other Minecraft instance?)");
        }
    }
}
