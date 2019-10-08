package net.yudichev.jiotty.connector.google.gmail;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.common.collect.Sets;
import net.yudichev.jiotty.common.async.ExecutorFactory;
import net.yudichev.jiotty.common.async.SchedulingExecutor;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.lang.Closeable;
import net.yudichev.jiotty.connector.google.gmail.Bindings.GmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;
import static net.yudichev.jiotty.common.lang.Runnables.guarded;
import static net.yudichev.jiotty.common.lang.throttling.ThresholdExceptionLoggingRunnable.withExceptionLoggedAfterThreshold;

final class GmailClientImpl extends BaseLifecycleComponent implements GmailClient {
    private static final Logger logger = LoggerFactory.getLogger(GmailClientImpl.class);
    private static final Duration MESSAGE_POLL_PERIOD = Duration.ofMinutes(5);
    private static final int MAX_ALLOWED_ERRORS_WHEN_POLLING = 3;

    private final Gmail gmail;
    private final InternalGmailObjectFactory internalGmailObjectFactory;
    private final ExecutorFactory executorFactory;
    private final Set<Subscription> subscriptions = Sets.newConcurrentHashSet();
    private SchedulingExecutor executor;

    @Inject
    GmailClientImpl(@GmailService Gmail gmail,
                    InternalGmailObjectFactory internalGmailObjectFactory,
                    ExecutorFactory executorFactory) {
        this.gmail = checkNotNull(gmail);
        this.internalGmailObjectFactory = checkNotNull(internalGmailObjectFactory);
        this.executorFactory = checkNotNull(executorFactory);
    }

    @SuppressWarnings("ReturnOfInnerClass") // we are a singleton
    @Override
    public Closeable subscribe(String query, Consumer<GmailMessage> handler) {
        Subscription subscription = new Subscription(query, handler);
        subscriptions.add(subscription);
        executor.execute(subscription::execute);
        return subscription;
    }

    @Override
    public CompletableFuture<Void> send(MessageComposer messageComposer) {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage email = new MimeMessage(session);
        asUnchecked(() -> messageComposer.accept(email));

        return supplyAsync(() -> getAsUnchecked(() -> {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            email.writeTo(buffer);
            String encodedEmail = Base64.encodeBase64URLSafeString(buffer.toByteArray());
            Message message = new Message();
            message.setRaw(encodedEmail);
            gmail.users().messages().send(Constants.ME, message).execute();
            return null;
        }));
    }

    @Override
    protected void doStart() {
        executor = executorFactory.createSingleThreadedSchedulingExecutor("gmail-client");
    }

    @Override
    protected void doStop() {
        subscriptions.forEach(Closeable::close);
        executor.close();
    }

    private final class Subscription implements Closeable {
        private final String query;
        private final Consumer<GmailMessage> handler;
        private Closeable schedule = Closeable.noop();
        private BigInteger historyId;

        Subscription(String query, Consumer<GmailMessage> handler) {
            this.query = checkNotNull(query);
            this.handler = checkNotNull(handler);
        }

        void execute() {
            synchronize();
            schedule = executor.scheduleAtFixedRate(MESSAGE_POLL_PERIOD,
                    withExceptionLoggedAfterThreshold(logger, "polling Gmail", MAX_ALLOWED_ERRORS_WHEN_POLLING, this::synchronize));
        }

        @Override
        public void close() {
            schedule.close();
            subscriptions.remove(this);
        }

        private void synchronize() {
            logger.debug("Synchronizing with historyId {}", historyId);
            Gmail.Users.Messages messagesRequests = gmail.users().messages();
            asUnchecked(() -> {
                String pageToken = null;
                AtomicReference<BigInteger> newHistoryIdRef = new AtomicReference<>();
                do {
                    ListMessagesResponse response = messagesRequests.list(Constants.ME)
                            .setQ(query)
                            .setPageToken(pageToken)
                            .execute();
                    if (response.getMessages() == null) {
                        //noinspection BreakStatement
                        break;
                    }
                    BatchRequest batch = gmail.batch();
                    JsonBatchCallback<Message> callback = new JsonBatchCallback<Message>() {
                        @SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
                        @Override
                        public void onSuccess(Message message, HttpHeaders responseHeaders) {
                            newHistoryIdRef.set(onMessage(message, newHistoryIdRef.get()));
                        }

                        @Override
                        public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
                            logger.error("Unable to batch load messages: {}, response headers: {}", e, responseHeaders);
                        }
                    };
                    response.getMessages().forEach(message ->
                            asUnchecked(() -> messagesRequests.get(Constants.ME, message.getId()).setFormat("full").queue(batch, callback)));
                    batch.execute();
                    pageToken = response.getNextPageToken();
                } while (pageToken != null);
                BigInteger newHistoryId = newHistoryIdRef.get();
                if (newHistoryId != null) {
                    historyId = newHistoryId;
                }
            });
        }

        private BigInteger onMessage(Message message, BigInteger runningHistoryId) {
            BigInteger messageHistoryId = message.getHistoryId();
            if (historyId == null || messageHistoryId.compareTo(historyId) > 0) {
                if (runningHistoryId == null) {
                    runningHistoryId = messageHistoryId;
                } else {
                    checkState(messageHistoryId.compareTo(runningHistoryId) < 0, "historyId went forward %s -> %s!", runningHistoryId, messageHistoryId);
                }
                guarded(logger, "handling gmail message", () -> handler.accept(internalGmailObjectFactory.createMessage(message))).run();
            }
            return runningHistoryId;
        }
    }
}
