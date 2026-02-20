package net.minecraft.nbt;

public interface StreamTagVisitor {

    StreamTagVisitor.ValueResult visitEnd();

    StreamTagVisitor.ValueResult visit(String value);

    StreamTagVisitor.ValueResult visit(byte value);

    StreamTagVisitor.ValueResult visit(short value);

    StreamTagVisitor.ValueResult visit(int value);

    StreamTagVisitor.ValueResult visit(long value);

    StreamTagVisitor.ValueResult visit(float value);

    StreamTagVisitor.ValueResult visit(double value);

    StreamTagVisitor.ValueResult visit(byte[] value);

    StreamTagVisitor.ValueResult visit(int[] value);

    StreamTagVisitor.ValueResult visit(long[] value);

    StreamTagVisitor.ValueResult visitList(TagType<?> elementType, int size);

    StreamTagVisitor.EntryResult visitEntry(TagType<?> type);

    StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id);

    StreamTagVisitor.EntryResult visitElement(TagType<?> type, int index);

    StreamTagVisitor.ValueResult visitContainerEnd();

    StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type);

    public static enum ValueResult {

        CONTINUE, BREAK, HALT;

        private ValueResult() {}
    }

    public static enum EntryResult {

        ENTER, SKIP, BREAK, HALT;

        private EntryResult() {}
    }
}
