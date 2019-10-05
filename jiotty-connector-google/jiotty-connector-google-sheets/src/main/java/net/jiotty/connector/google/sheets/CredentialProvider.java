package net.jiotty.connector.google.sheets;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.common.collect.ImmutableList;
import net.jiotty.connector.google.common.impl.GoogleApiSettings;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.URL;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.jiotty.common.lang.MoreThrowables.getAsUnchecked;
import static net.jiotty.connector.google.common.impl.Bindings.Settings;
import static net.jiotty.connector.google.common.impl.GoogleAuthorization.authorize;

final class CredentialProvider implements Provider<Credential> {
    private final NetHttpTransport netHttpTransport;
    private final URL credentialsUrl;

    @Inject
    CredentialProvider(NetHttpTransport netHttpTransport,
                       @Settings GoogleApiSettings settings) {
        this.netHttpTransport = checkNotNull(netHttpTransport);
        credentialsUrl = settings.credentialsUrl();
    }

    @Override
    public Credential get() {
        return getAsUnchecked(() -> authorize(
                netHttpTransport,
                "gsheets",
                credentialsUrl,
                ImmutableList.of(SheetsScopes.SPREADSHEETS)).getCredential());
    }
}