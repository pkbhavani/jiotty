package net.yudichev.jiotty.connector.google.assistant;

import com.google.assistant.embedded.v1alpha2.AudioOutConfig;
import com.google.assistant.embedded.v1alpha2.DeviceConfig;
import com.google.assistant.embedded.v1alpha2.DialogStateIn;
import net.yudichev.jiotty.common.inject.BindingSpec;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;
import net.yudichev.jiotty.connector.google.common.GoogleApiAuthSettings;
import net.yudichev.jiotty.connector.google.common.impl.BaseGoogleServiceModule;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.jiotty.common.inject.BindingSpec.literally;

public final class GoogleAssistantClientModule extends BaseGoogleServiceModule implements ExposedKeyModule<GoogleAssistantClient> {
    private final BindingSpec<AudioOutConfig> audioOutConfig;
    private final BindingSpec<DialogStateIn> dialogStateIn;
    private final BindingSpec<DeviceConfig> deviceConfig;

    private GoogleAssistantClientModule(GoogleApiAuthSettings settings,
                                        BindingSpec<AudioOutConfig> audioOutConfig,
                                        BindingSpec<DialogStateIn> dialogStateIn,
                                        BindingSpec<DeviceConfig> deviceConfig) {
        super(settings);
        this.audioOutConfig = checkNotNull(audioOutConfig);
        this.dialogStateIn = checkNotNull(dialogStateIn);
        this.deviceConfig = checkNotNull(deviceConfig);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void doConfigure() {
        audioOutConfig.bind(AudioOutConfig.class)
                .annotatedWith(GoogleAssistantClientImpl.Dependency.class)
                .installedBy(this::installLifecycleComponentModule);
        dialogStateIn.bind(DialogStateIn.class)
                .annotatedWith(GoogleAssistantClientImpl.Dependency.class)
                .installedBy(this::installLifecycleComponentModule);
        deviceConfig.bind(DeviceConfig.class)
                .annotatedWith(GoogleAssistantClientImpl.Dependency.class)
                .installedBy(this::installLifecycleComponentModule);

        bind(getExposedKey()).to(boundLifecycleComponent(GoogleAssistantClientImpl.class));
        expose(getExposedKey());
    }

    public static final class Builder extends BaseBuilder<ExposedKeyModule<GoogleAssistantClient>, Builder> {
        private BindingSpec<AudioOutConfig> audioOutConfigSpec = literally(AudioOutConfig.newBuilder()
                .setEncoding(AudioOutConfig.Encoding.MP3)
                .setSampleRateHertz(16000)
                .setVolumePercentage(100)
                .build());
        private BindingSpec<DialogStateIn> dialogStateInSpec = literally(DialogStateIn.newBuilder()
                .setLanguageCode("en-US")
                .build());
        private BindingSpec<DeviceConfig> deviceConfigSpec = literally(DeviceConfig.newBuilder()
                .setDeviceId("device_id")
                .setDeviceModelId("device_model_id")
                .build());

        public Builder withAudioOutConfig(BindingSpec<AudioOutConfig> audioOutConfigSpec) {
            this.audioOutConfigSpec = checkNotNull(audioOutConfigSpec);
            return this;
        }

        public Builder withDialogStateIn(BindingSpec<DialogStateIn> dialogStateInSpec) {
            this.dialogStateInSpec = checkNotNull(dialogStateInSpec);
            return this;
        }

        public Builder withDeviceConfig(BindingSpec<DeviceConfig> deviceConfigSpec) {
            this.deviceConfigSpec = checkNotNull(deviceConfigSpec);
            return this;
        }

        @Override
        public ExposedKeyModule<GoogleAssistantClient> build() {
            return new GoogleAssistantClientModule(getSettings(), audioOutConfigSpec, dialogStateInSpec, deviceConfigSpec);
        }

        @Override
        protected Builder thisBuilder() {
            return this;
        }
    }
}
