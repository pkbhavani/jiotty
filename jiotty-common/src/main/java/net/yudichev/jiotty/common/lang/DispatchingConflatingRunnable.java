package net.yudichev.jiotty.common.lang;

import java.util.concurrent.Executor;

public final class DispatchingConflatingRunnable implements Runnable {
    private static final Object VALUE = new Object();
    private final DispatchingConflatingConsumer<Object> conflatingConsumer;

    public DispatchingConflatingRunnable(Executor executor, Runnable delegate) {
        conflatingConsumer = new DispatchingConflatingConsumer<>(executor, supplier -> {
            supplier.get();
            delegate.run();
        });
    }

    @Override
    public void run() {
        conflatingConsumer.accept(VALUE);
    }
}
