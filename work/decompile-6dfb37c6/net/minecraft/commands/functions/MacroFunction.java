package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class MacroFunction<T extends ExecutionCommandSource<T>> implements CommandFunction<T> {

    private static final DecimalFormat DECIMAL_FORMAT = (DecimalFormat) Util.make(new DecimalFormat("#", DecimalFormatSymbols.getInstance(Locale.ROOT)), (decimalformat) -> {
        decimalformat.setMaximumFractionDigits(15);
    });
    private static final int MAX_CACHE_ENTRIES = 8;
    private final List<String> parameters;
    private final Object2ObjectLinkedOpenHashMap<List<String>, InstantiatedFunction<T>> cache = new Object2ObjectLinkedOpenHashMap(8, 0.25F);
    private final Identifier id;
    private final List<MacroFunction.Entry<T>> entries;

    public MacroFunction(Identifier id, List<MacroFunction.Entry<T>> entries, List<String> parameters) {
        this.id = id;
        this.entries = entries;
        this.parameters = parameters;
    }

    @Override
    public Identifier id() {
        return this.id;
    }

    @Override
    public InstantiatedFunction<T> instantiate(@Nullable CompoundTag arguments, CommandDispatcher<T> dispatcher) throws FunctionInstantiationException {
        if (arguments == null) {
            throw new FunctionInstantiationException(Component.translatable("commands.function.error.missing_arguments", Component.translationArg(this.id())));
        } else {
            List<String> list = new ArrayList(this.parameters.size());

            for (String s : this.parameters) {
                Tag tag = arguments.get(s);

                if (tag == null) {
                    throw new FunctionInstantiationException(Component.translatable("commands.function.error.missing_argument", Component.translationArg(this.id()), s));
                }

                list.add(stringify(tag));
            }

            InstantiatedFunction<T> instantiatedfunction = (InstantiatedFunction) this.cache.getAndMoveToLast(list);

            if (instantiatedfunction != null) {
                return instantiatedfunction;
            } else {
                if (this.cache.size() >= 8) {
                    this.cache.removeFirst();
                }

                InstantiatedFunction<T> instantiatedfunction1 = this.substituteAndParse(this.parameters, list, dispatcher);

                this.cache.put(list, instantiatedfunction1);
                return instantiatedfunction1;
            }
        }
    }

    private static String stringify(Tag tag) {
        Objects.requireNonNull(tag);
        byte b0 = 0;
        String s;

        //$FF: b0->value
        //0->net/minecraft/nbt/FloatTag
        //1->net/minecraft/nbt/DoubleTag
        //2->net/minecraft/nbt/ByteTag
        //3->net/minecraft/nbt/ShortTag
        //4->net/minecraft/nbt/LongTag
        //5->net/minecraft/nbt/StringTag
        switch (tag.typeSwitch<invokedynamic>(tag, b0)) {
            case 0:
                FloatTag floattag = (FloatTag)tag;
                FloatTag floattag1 = floattag;

                try {
                    f = floattag1.value();
                } catch (Throwable throwable) {
                    throw new MatchException(throwable.toString(), throwable);
                }

                float f1 = f;

                s = MacroFunction.DECIMAL_FORMAT.format((double)f1);
                break;
            case 1:
                DoubleTag doubletag = (DoubleTag)tag;
                DoubleTag doubletag1 = doubletag;

                try {
                    d0 = doubletag1.value();
                } catch (Throwable throwable1) {
                    throw new MatchException(throwable1.toString(), throwable1);
                }

                double d1 = d0;

                s = MacroFunction.DECIMAL_FORMAT.format(d1);
                break;
            case 2:
                ByteTag bytetag = (ByteTag)tag;
                ByteTag bytetag1 = bytetag;

                try {
                    b1 = bytetag1.value();
                } catch (Throwable throwable2) {
                    throw new MatchException(throwable2.toString(), throwable2);
                }

                byte b2 = b1;

                s = String.valueOf(b2);
                break;
            case 3:
                ShortTag shorttag = (ShortTag)tag;
                ShortTag shorttag1 = shorttag;

                try {
                    short0 = shorttag1.value();
                } catch (Throwable throwable3) {
                    throw new MatchException(throwable3.toString(), throwable3);
                }

                short short1 = short0;

                s = String.valueOf(short1);
                break;
            case 4:
                LongTag longtag = (LongTag)tag;
                LongTag longtag1 = longtag;

                try {
                    i = longtag1.value();
                } catch (Throwable throwable4) {
                    throw new MatchException(throwable4.toString(), throwable4);
                }

                long j = i;

                s = String.valueOf(j);
                break;
            case 5:
                StringTag stringtag = (StringTag)tag;
                StringTag stringtag1 = stringtag;

                try {
                    s1 = stringtag1.value();
                } catch (Throwable throwable5) {
                    throw new MatchException(throwable5.toString(), throwable5);
                }

                String s2 = s1;

                s = s2;
                break;
            default:
                s = tag.toString();
        }

        return s;
    }

    private static void lookupValues(List<String> values, IntList indicesToSelect, List<String> selectedValuesOutput) {
        selectedValuesOutput.clear();
        indicesToSelect.forEach((i) -> {
            selectedValuesOutput.add((String) values.get(i));
        });
    }

    private InstantiatedFunction<T> substituteAndParse(List<String> keys, List<String> values, CommandDispatcher<T> dispatcher) throws FunctionInstantiationException {
        List<UnboundEntryAction<T>> list2 = new ArrayList(this.entries.size());
        List<String> list3 = new ArrayList(values.size());

        for (MacroFunction.Entry<T> macrofunction_entry : this.entries) {
            lookupValues(values, macrofunction_entry.parameters(), list3);
            list2.add(macrofunction_entry.instantiate(list3, dispatcher, this.id));
        }

        return new PlainTextFunction<T>(this.id().withPath((s) -> {
            return s + "/" + keys.hashCode();
        }), list2);
    }

    static class PlainTextEntry<T> implements MacroFunction.Entry<T> {

        private final UnboundEntryAction<T> compiledAction;

        public PlainTextEntry(UnboundEntryAction<T> compiledAction) {
            this.compiledAction = compiledAction;
        }

        @Override
        public IntList parameters() {
            return IntLists.emptyList();
        }

        @Override
        public UnboundEntryAction<T> instantiate(List<String> substitutions, CommandDispatcher<T> dispatcher, Identifier functionId) {
            return this.compiledAction;
        }
    }

    static class MacroEntry<T extends ExecutionCommandSource<T>> implements MacroFunction.Entry<T> {

        private final StringTemplate template;
        private final IntList parameters;
        private final T compilationContext;

        public MacroEntry(StringTemplate template, IntList parameters, T compilationContext) {
            this.template = template;
            this.parameters = parameters;
            this.compilationContext = compilationContext;
        }

        @Override
        public IntList parameters() {
            return this.parameters;
        }

        @Override
        public UnboundEntryAction<T> instantiate(List<String> substitutions, CommandDispatcher<T> dispatcher, Identifier functionId) throws FunctionInstantiationException {
            String s = this.template.substitute(substitutions);

            try {
                return CommandFunction.<T>parseCommand(dispatcher, this.compilationContext, new StringReader(s));
            } catch (CommandSyntaxException commandsyntaxexception) {
                throw new FunctionInstantiationException(Component.translatable("commands.function.error.parse", Component.translationArg(functionId), s, commandsyntaxexception.getMessage()));
            }
        }
    }

    interface Entry<T> {

        IntList parameters();

        UnboundEntryAction<T> instantiate(List<String> substitutions, CommandDispatcher<T> dispatcher, Identifier funtionId) throws FunctionInstantiationException;
    }
}
