package net.minecraft.world.level.levelgen.structure.pieces;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import org.jspecify.annotations.Nullable;

public class StructurePiecesBuilder implements StructurePieceAccessor {

    private final List<StructurePiece> pieces = Lists.newArrayList();

    public StructurePiecesBuilder() {}

    @Override
    public void addPiece(StructurePiece piece) {
        this.pieces.add(piece);
    }

    @Override
    public @Nullable StructurePiece findCollisionPiece(BoundingBox box) {
        return StructurePiece.findCollisionPiece(this.pieces, box);
    }

    /** @deprecated */
    @Deprecated
    public void offsetPiecesVertically(int dy) {
        for (StructurePiece structurepiece : this.pieces) {
            structurepiece.move(0, dy, 0);
        }

    }

    /** @deprecated */
    @Deprecated
    public int moveBelowSeaLevel(int seaLevel, int minY, RandomSource random, int offset) {
        int l = seaLevel - offset;
        BoundingBox boundingbox = this.getBoundingBox();
        int i1 = boundingbox.getYSpan() + minY + 1;

        if (i1 < l) {
            i1 += random.nextInt(l - i1);
        }

        int j1 = i1 - boundingbox.maxY();

        this.offsetPiecesVertically(j1);
        return j1;
    }

    /** @deprecated */
    public void moveInsideHeights(RandomSource random, int lowestAllowed, int highestAllowed) {
        BoundingBox boundingbox = this.getBoundingBox();
        int k = highestAllowed - lowestAllowed + 1 - boundingbox.getYSpan();
        int l;

        if (k > 1) {
            l = lowestAllowed + random.nextInt(k);
        } else {
            l = lowestAllowed;
        }

        int i1 = l - boundingbox.minY();

        this.offsetPiecesVertically(i1);
    }

    public PiecesContainer build() {
        return new PiecesContainer(this.pieces);
    }

    public void clear() {
        this.pieces.clear();
    }

    public boolean isEmpty() {
        return this.pieces.isEmpty();
    }

    public BoundingBox getBoundingBox() {
        return StructurePiece.createBoundingBox(this.pieces.stream());
    }
}
