package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.util.DelegateDataOutput;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class NbtIo {

    private static final OpenOption[] SYNC_OUTPUT_OPTIONS = new OpenOption[]{StandardOpenOption.SYNC, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

    public NbtIo() {}

    public static CompoundTag readCompressed(Path file, NbtAccounter accounter) throws IOException {
        CompoundTag compoundtag;

        try (InputStream inputstream = Files.newInputStream(file); InputStream inputstream1 = new FastBufferedInputStream(inputstream);) {
            compoundtag = readCompressed(inputstream1, accounter);
        }

        return compoundtag;
    }

    private static DataInputStream createDecompressorStream(InputStream in) throws IOException {
        return new DataInputStream(new FastBufferedInputStream(new GZIPInputStream(in)));
    }

    private static DataOutputStream createCompressorStream(OutputStream out) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(out)));
    }

    public static CompoundTag readCompressed(InputStream in, NbtAccounter accounter) throws IOException {
        try (DataInputStream datainputstream = createDecompressorStream(in)) {
            return read(datainputstream, accounter);
        }
    }

    public static void parseCompressed(Path file, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
        try (InputStream inputstream = Files.newInputStream(file); InputStream inputstream1 = new FastBufferedInputStream(inputstream);) {
            parseCompressed(inputstream1, output, accounter);
        }

    }

    public static void parseCompressed(InputStream in, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
        try (DataInputStream datainputstream = createDecompressorStream(in)) {
            parse(datainputstream, output, accounter);
        }

    }

    public static void writeCompressed(CompoundTag tag, Path file) throws IOException {
        try (OutputStream outputstream = Files.newOutputStream(file, NbtIo.SYNC_OUTPUT_OPTIONS); OutputStream outputstream1 = new BufferedOutputStream(outputstream);) {
            writeCompressed(tag, outputstream1);
        }

    }

    public static void writeCompressed(CompoundTag tag, OutputStream out) throws IOException {
        try (DataOutputStream dataoutputstream = createCompressorStream(out)) {
            write(tag, (DataOutput) dataoutputstream);
        }

    }

    public static void write(CompoundTag tag, Path file) throws IOException {
        try (OutputStream outputstream = Files.newOutputStream(file, NbtIo.SYNC_OUTPUT_OPTIONS); OutputStream outputstream1 = new BufferedOutputStream(outputstream); DataOutputStream dataoutputstream = new DataOutputStream(outputstream1);) {
            write(tag, (DataOutput) dataoutputstream);
        }

    }

    public static @Nullable CompoundTag read(Path file) throws IOException {
        if (!Files.exists(file, new LinkOption[0])) {
            return null;
        } else {
            CompoundTag compoundtag;

            try (InputStream inputstream = Files.newInputStream(file); DataInputStream datainputstream = new DataInputStream(inputstream);) {
                compoundtag = read(datainputstream, NbtAccounter.unlimitedHeap());
            }

            return compoundtag;
        }
    }

    public static CompoundTag read(DataInput input) throws IOException {
        return read(input, NbtAccounter.unlimitedHeap());
    }

    public static CompoundTag read(DataInput input, NbtAccounter accounter) throws IOException {
        Tag tag = readUnnamedTag(input, accounter);

        if (tag instanceof CompoundTag) {
            return (CompoundTag) tag;
        } else {
            throw new IOException("Root tag must be a named compound tag");
        }
    }

    public static void write(CompoundTag tag, DataOutput output) throws IOException {
        writeUnnamedTagWithFallback(tag, output);
    }

    public static void parse(DataInput input, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
        TagType<?> tagtype = TagTypes.getType(input.readByte());

        if (tagtype == EndTag.TYPE) {
            if (output.visitRootEntry(EndTag.TYPE) == StreamTagVisitor.ValueResult.CONTINUE) {
                output.visitEnd();
            }

        } else {
            switch (output.visitRootEntry(tagtype)) {
                case HALT:
                default:
                    break;
                case BREAK:
                    StringTag.skipString(input);
                    tagtype.skip(input, accounter);
                    break;
                case CONTINUE:
                    StringTag.skipString(input);
                    tagtype.parse(input, output, accounter);
            }

        }
    }

    public static Tag readAnyTag(DataInput input, NbtAccounter accounter) throws IOException {
        byte b0 = input.readByte();

        return (Tag) (b0 == 0 ? EndTag.INSTANCE : readTagSafe(input, accounter, b0));
    }

    public static void writeAnyTag(Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            tag.write(output);
        }
    }

    public static void writeUnnamedTag(Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            output.writeUTF("");
            tag.write(output);
        }
    }

    public static void writeUnnamedTagWithFallback(Tag tag, DataOutput output) throws IOException {
        writeUnnamedTag(tag, new NbtIo.StringFallbackDataOutput(output));
    }

    @VisibleForTesting
    public static Tag readUnnamedTag(DataInput input, NbtAccounter accounter) throws IOException {
        byte b0 = input.readByte();

        if (b0 == 0) {
            return EndTag.INSTANCE;
        } else {
            StringTag.skipString(input);
            return readTagSafe(input, accounter, b0);
        }
    }

    private static Tag readTagSafe(DataInput input, NbtAccounter accounter, byte type) {
        try {
            return TagTypes.getType(type).load(input, accounter);
        } catch (IOException ioexception) {
            CrashReport crashreport = CrashReport.forThrowable(ioexception, "Loading NBT data");
            CrashReportCategory crashreportcategory = crashreport.addCategory("NBT Tag");

            crashreportcategory.setDetail("Tag type", type);
            throw new ReportedNbtException(crashreport);
        }
    }

    public static class StringFallbackDataOutput extends DelegateDataOutput {

        public StringFallbackDataOutput(DataOutput parent) {
            super(parent);
        }

        @Override
        public void writeUTF(String s) throws IOException {
            try {
                super.writeUTF(s);
            } catch (UTFDataFormatException utfdataformatexception) {
                Util.logAndPauseIfInIde("Failed to write NBT String", utfdataformatexception);
                super.writeUTF("");
            }

        }
    }
}
