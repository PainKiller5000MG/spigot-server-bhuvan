package net.minecraft.commands;

@FunctionalInterface
public interface CommandResultCallback {

    CommandResultCallback EMPTY = new CommandResultCallback() {
        @Override
        public void onResult(boolean success, int result) {}

        public String toString() {
            return "<empty>";
        }
    };

    void onResult(boolean success, int result);

    default void onSuccess(int result) {
        this.onResult(true, result);
    }

    default void onFailure() {
        this.onResult(false, 0);
    }

    static CommandResultCallback chain(CommandResultCallback first, CommandResultCallback second) {
        return first == CommandResultCallback.EMPTY ? second : (second == CommandResultCallback.EMPTY ? first : (flag, i) -> {
            first.onResult(flag, i);
            second.onResult(flag, i);
        });
    }
}
