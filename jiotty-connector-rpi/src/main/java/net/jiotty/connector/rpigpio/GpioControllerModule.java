package net.jiotty.connector.rpigpio;

import com.pi4j.io.gpio.GpioController;
import net.jiotty.common.inject.BaseLifecycleComponentModule;
import net.jiotty.common.inject.ExposedKeyModule;
import net.jiotty.common.lang.TypedBuilder;

public final class GpioControllerModule extends BaseLifecycleComponentModule implements ExposedKeyModule<GpioController> {
    private GpioControllerModule() {
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void configure() {
        bind(getExposedKey()).toProvider(boundLifecycleComponent(GpioControllerProvider.class));
        expose(getExposedKey());
    }

    public static class Builder implements TypedBuilder<ExposedKeyModule<GpioController>> {
        @Override
        public ExposedKeyModule<GpioController> build() {
            return new GpioControllerModule();
        }
    }
}
