package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.util.datafix.PackedBitStorage;
import org.jspecify.annotations.Nullable;

public class LeavesFix extends DataFix {

    private static final int NORTH_WEST_MASK = 128;
    private static final int WEST_MASK = 64;
    private static final int SOUTH_WEST_MASK = 32;
    private static final int SOUTH_MASK = 16;
    private static final int SOUTH_EAST_MASK = 8;
    private static final int EAST_MASK = 4;
    private static final int NORTH_EAST_MASK = 2;
    private static final int NORTH_MASK = 1;
    private static final int[][] DIRECTIONS = new int[][]{{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    private static final int DECAY_DISTANCE = 7;
    private static final int SIZE_BITS = 12;
    private static final int SIZE = 4096;
    private static final Object2IntMap<String> LEAVES = (Object2IntMap) DataFixUtils.make(new Object2IntOpenHashMap(), (object2intopenhashmap) -> {
        object2intopenhashmap.put("minecraft:acacia_leaves", 0);
        object2intopenhashmap.put("minecraft:birch_leaves", 1);
        object2intopenhashmap.put("minecraft:dark_oak_leaves", 2);
        object2intopenhashmap.put("minecraft:jungle_leaves", 3);
        object2intopenhashmap.put("minecraft:oak_leaves", 4);
        object2intopenhashmap.put("minecraft:spruce_leaves", 5);
    });
    private static final Set<String> LOGS = ImmutableSet.of("minecraft:acacia_bark", "minecraft:birch_bark", "minecraft:dark_oak_bark", "minecraft:jungle_bark", "minecraft:oak_bark", "minecraft:spruce_bark", new String[]{"minecraft:acacia_log", "minecraft:birch_log", "minecraft:dark_oak_log", "minecraft:jungle_log", "minecraft:oak_log", "minecraft:spruce_log", "minecraft:stripped_acacia_log", "minecraft:stripped_birch_log", "minecraft:stripped_dark_oak_log", "minecraft:stripped_jungle_log", "minecraft:stripped_oak_log", "minecraft:stripped_spruce_log"});

    public LeavesFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticfinder = type.findField("Level");
        OpticFinder<?> opticfinder1 = opticfinder.type().findField("Sections");
        Type<?> type1 = opticfinder1.type();

        if (!(type1 instanceof ListType)) {
            throw new IllegalStateException("Expecting sections to be a list.");
        } else {
            Type<?> type2 = ((ListType) type1).getElement();
            OpticFinder<?> opticfinder2 = DSL.typeFinder(type2);

            return this.fixTypeEverywhereTyped("Leaves fix", type, (typed) -> {
                return typed.updateTyped(opticfinder, (typed1) -> {
                    int[] aint = new int[]{0};
                    Typed<?> typed2 = typed1.updateTyped(opticfinder1, (typed3) -> {
                        Int2ObjectMap<LeavesFix.LeavesSection> int2objectmap = new Int2ObjectOpenHashMap((Map) typed3.getAllTyped(opticfinder2).stream().map((typed4) -> {
                            return new LeavesFix.LeavesSection(typed4, this.getInputSchema());
                        }).collect(Collectors.toMap(LeavesFix.Section::getIndex, (leavesfix_leavessection) -> {
                            return leavesfix_leavessection;
                        })));

                        if (int2objectmap.values().stream().allMatch(LeavesFix.Section::isSkippable)) {
                            return typed3;
                        } else {
                            List<IntSet> list = Lists.newArrayList();

                            for (int i = 0; i < 7; ++i) {
                                list.add(new IntOpenHashSet());
                            }

                            ObjectIterator objectiterator = int2objectmap.values().iterator();

                            while (objectiterator.hasNext()) {
                                LeavesFix.LeavesSection leavesfix_leavessection = (LeavesFix.LeavesSection) objectiterator.next();

                                if (!leavesfix_leavessection.isSkippable()) {
                                    for (int j = 0; j < 4096; ++j) {
                                        int k = leavesfix_leavessection.getBlock(j);

                                        if (leavesfix_leavessection.isLog(k)) {
                                            ((IntSet) list.get(0)).add(leavesfix_leavessection.getIndex() << 12 | j);
                                        } else if (leavesfix_leavessection.isLeaf(k)) {
                                            int l = this.getX(j);
                                            int i1 = this.getZ(j);

                                            aint[0] |= getSideMask(l == 0, l == 15, i1 == 0, i1 == 15);
                                        }
                                    }
                                }
                            }

                            for (int j1 = 1; j1 < 7; ++j1) {
                                IntSet intset = (IntSet) list.get(j1 - 1);
                                IntSet intset1 = (IntSet) list.get(j1);
                                IntIterator intiterator = intset.iterator();

                                while (intiterator.hasNext()) {
                                    int k1 = intiterator.nextInt();
                                    int l1 = this.getX(k1);
                                    int i2 = this.getY(k1);
                                    int j2 = this.getZ(k1);

                                    for (int[] aint1 : LeavesFix.DIRECTIONS) {
                                        int k2 = l1 + aint1[0];
                                        int l2 = i2 + aint1[1];
                                        int i3 = j2 + aint1[2];

                                        if (k2 >= 0 && k2 <= 15 && i3 >= 0 && i3 <= 15 && l2 >= 0 && l2 <= 255) {
                                            LeavesFix.LeavesSection leavesfix_leavessection1 = (LeavesFix.LeavesSection) int2objectmap.get(l2 >> 4);

                                            if (leavesfix_leavessection1 != null && !leavesfix_leavessection1.isSkippable()) {
                                                int j3 = getIndex(k2, l2 & 15, i3);
                                                int k3 = leavesfix_leavessection1.getBlock(j3);

                                                if (leavesfix_leavessection1.isLeaf(k3)) {
                                                    int l3 = leavesfix_leavessection1.getDistance(k3);

                                                    if (l3 > j1) {
                                                        leavesfix_leavessection1.setDistance(j3, k3, j1);
                                                        intset1.add(getIndex(k2, l2, i3));
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            return typed3.updateTyped(opticfinder2, (typed4) -> {
                                return ((LeavesFix.LeavesSection) int2objectmap.get(((Dynamic) typed4.get(DSL.remainderFinder())).get("Y").asInt(0))).write(typed4);
                            });
                        }
                    });

                    if (aint[0] != 0) {
                        typed2 = typed2.update(DSL.remainderFinder(), (dynamic) -> {
                            Dynamic<?> dynamic1 = (Dynamic) DataFixUtils.orElse(dynamic.get("UpgradeData").result(), dynamic.emptyMap());

                            return dynamic.set("UpgradeData", dynamic1.set("Sides", dynamic.createByte((byte) (dynamic1.get("Sides").asByte((byte) 0) | aint[0]))));
                        });
                    }

                    return typed2;
                });
            });
        }
    }

    public static int getIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    private int getX(int index) {
        return index & 15;
    }

    private int getY(int index) {
        return index >> 8 & 255;
    }

    private int getZ(int index) {
        return index >> 4 & 15;
    }

    public static int getSideMask(boolean west, boolean east, boolean north, boolean south) {
        int i = 0;

        if (north) {
            if (east) {
                i |= 2;
            } else if (west) {
                i |= 128;
            } else {
                i |= 1;
            }
        } else if (south) {
            if (west) {
                i |= 32;
            } else if (east) {
                i |= 8;
            } else {
                i |= 16;
            }
        } else if (east) {
            i |= 4;
        } else if (west) {
            i |= 64;
        }

        return i;
    }

    public abstract static class Section {

        protected static final String BLOCK_STATES_TAG = "BlockStates";
        protected static final String NAME_TAG = "Name";
        protected static final String PROPERTIES_TAG = "Properties";
        private final Type<Pair<String, Dynamic<?>>> blockStateType;
        protected final OpticFinder<List<Pair<String, Dynamic<?>>>> paletteFinder;
        protected final List<Dynamic<?>> palette;
        protected final int index;
        protected @Nullable PackedBitStorage storage;

        public Section(Typed<?> section, Schema inputSchema) {
            this.blockStateType = DSL.named(References.BLOCK_STATE.typeName(), DSL.remainderType());
            this.paletteFinder = DSL.fieldFinder("Palette", DSL.list(this.blockStateType));
            if (!Objects.equals(inputSchema.getType(References.BLOCK_STATE), this.blockStateType)) {
                throw new IllegalStateException("Block state type is not what was expected.");
            } else {
                Optional<List<Pair<String, Dynamic<?>>>> optional = section.getOptional(this.paletteFinder);

                this.palette = (List) optional.map((list) -> {
                    return (List) list.stream().map(Pair::getSecond).collect(Collectors.toList());
                }).orElse(ImmutableList.of());
                Dynamic<?> dynamic = (Dynamic) section.get(DSL.remainderFinder());

                this.index = dynamic.get("Y").asInt(0);
                this.readStorage(dynamic);
            }
        }

        protected void readStorage(Dynamic<?> tag) {
            if (this.skippable()) {
                this.storage = null;
            } else {
                long[] along = tag.get("BlockStates").asLongStream().toArray();
                int i = Math.max(4, DataFixUtils.ceillog2(this.palette.size()));

                this.storage = new PackedBitStorage(i, 4096, along);
            }

        }

        public Typed<?> write(Typed<?> section) {
            return this.isSkippable() ? section : section.update(DSL.remainderFinder(), (dynamic) -> {
                return dynamic.set("BlockStates", dynamic.createLongList(Arrays.stream(this.storage.getRaw())));
            }).set(this.paletteFinder, (List) this.palette.stream().map((dynamic) -> {
                return Pair.of(References.BLOCK_STATE.typeName(), dynamic);
            }).collect(Collectors.toList()));
        }

        public boolean isSkippable() {
            return this.storage == null;
        }

        public int getBlock(int pos) {
            return this.storage.get(pos);
        }

        protected int getStateId(String blockName, boolean persistent, int distance) {
            return LeavesFix.LEAVES.get(blockName) << 5 | (persistent ? 16 : 0) | distance;
        }

        int getIndex() {
            return this.index;
        }

        protected abstract boolean skippable();
    }

    public static final class LeavesSection extends LeavesFix.Section {

        private static final String PERSISTENT = "persistent";
        private static final String DECAYABLE = "decayable";
        private static final String DISTANCE = "distance";
        private @Nullable IntSet leaveIds;
        private @Nullable IntSet logIds;
        private @Nullable Int2IntMap stateToIdMap;

        public LeavesSection(Typed<?> section, Schema inputSchema) {
            super(section, inputSchema);
        }

        @Override
        protected boolean skippable() {
            this.leaveIds = new IntOpenHashSet();
            this.logIds = new IntOpenHashSet();
            this.stateToIdMap = new Int2IntOpenHashMap();

            for (int i = 0; i < this.palette.size(); ++i) {
                Dynamic<?> dynamic = (Dynamic) this.palette.get(i);
                String s = dynamic.get("Name").asString("");

                if (LeavesFix.LEAVES.containsKey(s)) {
                    boolean flag = Objects.equals(dynamic.get("Properties").get("decayable").asString(""), "false");

                    this.leaveIds.add(i);
                    this.stateToIdMap.put(this.getStateId(s, flag, 7), i);
                    this.palette.set(i, this.makeLeafTag(dynamic, s, flag, 7));
                }

                if (LeavesFix.LOGS.contains(s)) {
                    this.logIds.add(i);
                }
            }

            return this.leaveIds.isEmpty() && this.logIds.isEmpty();
        }

        private Dynamic<?> makeLeafTag(Dynamic<?> input, String blockName, boolean persistent, int distance) {
            Dynamic<?> dynamic1 = input.emptyMap();

            dynamic1 = dynamic1.set("persistent", dynamic1.createString(persistent ? "true" : "false"));
            dynamic1 = dynamic1.set("distance", dynamic1.createString(Integer.toString(distance)));
            Dynamic<?> dynamic2 = input.emptyMap();

            dynamic2 = dynamic2.set("Properties", dynamic1);
            dynamic2 = dynamic2.set("Name", dynamic2.createString(blockName));
            return dynamic2;
        }

        public boolean isLog(int block) {
            return this.logIds.contains(block);
        }

        public boolean isLeaf(int block) {
            return this.leaveIds.contains(block);
        }

        private int getDistance(int block) {
            return this.isLog(block) ? 0 : Integer.parseInt(((Dynamic) this.palette.get(block)).get("Properties").get("distance").asString(""));
        }

        private void setDistance(int pos, int block, int distance) {
            Dynamic<?> dynamic = (Dynamic) this.palette.get(block);
            String s = dynamic.get("Name").asString("");
            boolean flag = Objects.equals(dynamic.get("Properties").get("persistent").asString(""), "true");
            int l = this.getStateId(s, flag, distance);

            if (!this.stateToIdMap.containsKey(l)) {
                int i1 = this.palette.size();

                this.leaveIds.add(i1);
                this.stateToIdMap.put(l, i1);
                this.palette.add(this.makeLeafTag(dynamic, s, flag, distance));
            }

            int j1 = this.stateToIdMap.get(l);

            if (1 << this.storage.getBits() <= j1) {
                PackedBitStorage packedbitstorage = new PackedBitStorage(this.storage.getBits() + 1, 4096);

                for (int k1 = 0; k1 < 4096; ++k1) {
                    packedbitstorage.set(k1, this.storage.get(k1));
                }

                this.storage = packedbitstorage;
            }

            this.storage.set(pos, j1);
        }
    }
}
