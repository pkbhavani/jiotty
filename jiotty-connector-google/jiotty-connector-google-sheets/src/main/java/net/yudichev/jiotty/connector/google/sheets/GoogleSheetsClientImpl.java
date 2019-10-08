package net.yudichev.jiotty.connector.google.sheets;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class GoogleSheetsClientImpl implements GoogleSheetsClient {
    private final com.google.api.services.sheets.v4.Sheets sheets;
    private final GoogleSpreadsheetFactory spreadsheetFactory;

    @Inject
    GoogleSheetsClientImpl(@Bindings.Internal com.google.api.services.sheets.v4.Sheets sheets,
                           GoogleSpreadsheetFactory spreadsheetFactory) {
        this.sheets = checkNotNull(sheets);
        this.spreadsheetFactory = checkNotNull(spreadsheetFactory);
    }

    @Override
    public CompletableFuture<GoogleSpreadsheet> getSpreadsheet(String spreadsheetId) {
        return supplyAsync(() -> getAsUnchecked(() -> sheets.spreadsheets().get(spreadsheetId)
                .execute()))
                .thenApply(spreadsheetFactory::create);
    }

}
