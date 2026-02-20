package net.minecraft.world.level;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public abstract class BaseCommandBlock {

    private static final Component DEFAULT_NAME = Component.literal("@");
    private static final int NO_LAST_EXECUTION = -1;
    private long lastExecution = -1L;
    private boolean updateLastExecution = true;
    private int successCount;
    private boolean trackOutput = true;
    private @Nullable Component lastOutput;
    private String command = "";
    private @Nullable Component customName;

    public BaseCommandBlock() {}

    public int getSuccessCount() {
        return this.successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public Component getLastOutput() {
        return this.lastOutput == null ? CommonComponents.EMPTY : this.lastOutput;
    }

    public void save(ValueOutput output) {
        output.putString("Command", this.command);
        output.putInt("SuccessCount", this.successCount);
        output.storeNullable("CustomName", ComponentSerialization.CODEC, this.customName);
        output.putBoolean("TrackOutput", this.trackOutput);
        if (this.trackOutput) {
            output.storeNullable("LastOutput", ComponentSerialization.CODEC, this.lastOutput);
        }

        output.putBoolean("UpdateLastExecution", this.updateLastExecution);
        if (this.updateLastExecution && this.lastExecution != -1L) {
            output.putLong("LastExecution", this.lastExecution);
        }

    }

    public void load(ValueInput input) {
        this.command = input.getStringOr("Command", "");
        this.successCount = input.getIntOr("SuccessCount", 0);
        this.setCustomName(BlockEntity.parseCustomNameSafe(input, "CustomName"));
        this.trackOutput = input.getBooleanOr("TrackOutput", true);
        if (this.trackOutput) {
            this.lastOutput = BlockEntity.parseCustomNameSafe(input, "LastOutput");
        } else {
            this.lastOutput = null;
        }

        this.updateLastExecution = input.getBooleanOr("UpdateLastExecution", true);
        if (this.updateLastExecution) {
            this.lastExecution = input.getLongOr("LastExecution", -1L);
        } else {
            this.lastExecution = -1L;
        }

    }

    public void setCommand(String command) {
        this.command = command;
        this.successCount = 0;
    }

    public String getCommand() {
        return this.command;
    }

    public boolean performCommand(ServerLevel level) {
        if (level.getGameTime() == this.lastExecution) {
            return false;
        } else if ("Searge".equalsIgnoreCase(this.command)) {
            this.lastOutput = Component.literal("#itzlipofutzli");
            this.successCount = 1;
            return true;
        } else {
            this.successCount = 0;
            if (level.isCommandBlockEnabled() && !StringUtil.isNullOrEmpty(this.command)) {
                try {
                    this.lastOutput = null;

                    try (BaseCommandBlock.CloseableCommandBlockSource basecommandblock_closeablecommandblocksource = this.createSource(level)) {
                        CommandSource commandsource = (CommandSource) Objects.requireNonNullElse(basecommandblock_closeablecommandblocksource, CommandSource.NULL);
                        CommandSourceStack commandsourcestack = this.createCommandSourceStack(level, commandsource).withCallback((flag, i) -> {
                            if (flag) {
                                ++this.successCount;
                            }

                        });

                        level.getServer().getCommands().performPrefixedCommand(commandsourcestack, this.command);
                    }
                } catch (Throwable throwable) {
                    CrashReport crashreport = CrashReport.forThrowable(throwable, "Executing command block");
                    CrashReportCategory crashreportcategory = crashreport.addCategory("Command to be executed");

                    crashreportcategory.setDetail("Command", this::getCommand);
                    crashreportcategory.setDetail("Name", () -> {
                        return this.getName().getString();
                    });
                    throw new ReportedException(crashreport);
                }
            }

            if (this.updateLastExecution) {
                this.lastExecution = level.getGameTime();
            } else {
                this.lastExecution = -1L;
            }

            return true;
        }
    }

    public BaseCommandBlock.@Nullable CloseableCommandBlockSource createSource(ServerLevel level) {
        return this.trackOutput ? new BaseCommandBlock.CloseableCommandBlockSource(level) : null;
    }

    public Component getName() {
        return this.customName != null ? this.customName : BaseCommandBlock.DEFAULT_NAME;
    }

    public @Nullable Component getCustomName() {
        return this.customName;
    }

    public void setCustomName(@Nullable Component name) {
        this.customName = name;
    }

    public abstract void onUpdated(ServerLevel level);

    public void setLastOutput(@Nullable Component lastOutput) {
        this.lastOutput = lastOutput;
    }

    public void setTrackOutput(boolean trackOutput) {
        this.trackOutput = trackOutput;
    }

    public boolean isTrackOutput() {
        return this.trackOutput;
    }

    public abstract CommandSourceStack createCommandSourceStack(ServerLevel level, CommandSource source);

    public abstract boolean isValid();

    protected class CloseableCommandBlockSource implements CommandSource, AutoCloseable {

        private final ServerLevel level;
        private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
        private boolean closed;

        protected CloseableCommandBlockSource(ServerLevel level) {
            this.level = level;
        }

        @Override
        public boolean acceptsSuccess() {
            return !this.closed && (Boolean) this.level.getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK);
        }

        @Override
        public boolean acceptsFailure() {
            return !this.closed;
        }

        @Override
        public boolean shouldInformAdmins() {
            return !this.closed && (Boolean) this.level.getGameRules().get(GameRules.COMMAND_BLOCK_OUTPUT);
        }

        @Override
        public void sendSystemMessage(Component message) {
            if (!this.closed) {
                DateTimeFormatter datetimeformatter = BaseCommandBlock.CloseableCommandBlockSource.TIME_FORMAT;

                BaseCommandBlock.this.lastOutput = Component.literal("[" + datetimeformatter.format(ZonedDateTime.now()) + "] ").append(message);
                BaseCommandBlock.this.onUpdated(this.level);
            }

        }

        public void close() throws Exception {
            this.closed = true;
        }
    }
}
