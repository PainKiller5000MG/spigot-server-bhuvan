package net.minecraft.nbt.visitors;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.TagType;

public class SkipFields extends CollectToTag {

    private final Deque<FieldTree> stack = new ArrayDeque();

    public SkipFields(FieldSelector... wantedFields) {
        FieldTree fieldtree = FieldTree.createRoot();

        for (FieldSelector fieldselector : wantedFields) {
            fieldtree.addEntry(fieldselector);
        }

        this.stack.push(fieldtree);
    }

    @Override
    public StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id) {
        FieldTree fieldtree = (FieldTree) this.stack.element();

        if (fieldtree.isSelected(type, id)) {
            return StreamTagVisitor.EntryResult.SKIP;
        } else {
            if (type == CompoundTag.TYPE) {
                FieldTree fieldtree1 = (FieldTree) fieldtree.fieldsToRecurse().get(id);

                if (fieldtree1 != null) {
                    this.stack.push(fieldtree1);
                }
            }

            return super.visitEntry(type, id);
        }
    }

    @Override
    public StreamTagVisitor.ValueResult visitContainerEnd() {
        if (this.depth() == ((FieldTree) this.stack.element()).depth()) {
            this.stack.pop();
        }

        return super.visitContainerEnd();
    }
}
