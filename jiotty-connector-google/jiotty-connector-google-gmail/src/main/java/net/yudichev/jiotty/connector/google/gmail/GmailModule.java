package net.yudichev.jiotty.connector.google.gmail;

import com.google.api.services.gmail.Gmail;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;
import net.yudichev.jiotty.connector.google.common.GoogleApiAuthSettings;
import net.yudichev.jiotty.connector.google.common.impl.BaseGoogleServiceModule;

import javax.inject.Singleton;

public final class GmailModule extends BaseGoogleServiceModule implements ExposedKeyModule<GmailClient> {
    private GmailModule(GoogleApiAuthSettings settings) {
        super(settings);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void doConfigure() {
        install(new FactoryModuleBuilder()
                .implement(GmailMessage.class, InternalGmailMessage.class)
                .implement(GmailMessageAttachment.class, InternalGmailMessageAttachment.class)
                .build(InternalGmailObjectFactory.class));

        bind(Gmail.class).annotatedWith(Bindings.GmailService.class).toProvider(GmailProvider.class).in(Singleton.class);
        bind(getExposedKey()).to(boundLifecycleComponent(GmailClientImpl.class));
        expose(getExposedKey());
    }

    public static final class Builder extends BaseBuilder<ExposedKeyModule<GmailClient>, Builder> {
        @Override
        public ExposedKeyModule<GmailClient> build() {
            return new GmailModule(getSettings());
        }

        @Override
        protected Builder thisBuilder() {
            return this;
        }
    }
}
