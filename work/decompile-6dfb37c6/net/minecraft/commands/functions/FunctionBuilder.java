package net.minecraft.commands.functions;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

class FunctionBuilder<T extends ExecutionCommandSource<T>> {

    private @Nullable List<UnboundEntryAction<T>> plainEntries = new ArrayList();
    private @Nullable List<MacroFunction.Entry<T>> macroEntries;
    private final List<String> macroArguments = new ArrayList();

    FunctionBuilder() {}

    public void addCommand(UnboundEntryAction<T> command) {
        if (this.macroEntries != null) {
            this.macroEntries.add(new MacroFunction.PlainTextEntry(command));
        } else {
            this.plainEntries.add(command);
        }

    }

    private int getArgumentIndex(String id) {
        int i = this.macroArguments.indexOf(id);

        if (i == -1) {
            i = this.macroArguments.size();
            this.macroArguments.add(id);
        }

        return i;
    }

    private IntList convertToIndices(List<String> ids) {
        IntArrayList intarraylist = new IntArrayList(ids.size());

        for (String s : ids) {
            intarraylist.add(this.getArgumentIndex(s));
        }

        return intarraylist;
    }

    public void addMacro(String command, int line, T compilationContext) {
        StringTemplate stringtemplate;

        try {
            stringtemplate = StringTemplate.fromString(command);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Can't parse function line " + line + ": '" + command + "'", exception);
        }

        if (this.plainEntries != null) {
            this.macroEntries = new ArrayList(this.plainEntries.size() + 1);

            for (UnboundEntryAction<T> unboundentryaction : this.plainEntries) {
                this.macroEntries.add(new MacroFunction.PlainTextEntry(unboundentryaction));
            }

            this.plainEntries = null;
        }

        this.macroEntries.add(new MacroFunction.MacroEntry(stringtemplate, this.convertToIndices(stringtemplate.variables()), compilationContext));
    }

    public CommandFunction<T> build(Identifier id) {
        return (CommandFunction<T>) (this.macroEntries != null ? new MacroFunction(id, this.macroEntries, this.macroArguments) : new PlainTextFunction(id, this.plainEntries));
    }
}
