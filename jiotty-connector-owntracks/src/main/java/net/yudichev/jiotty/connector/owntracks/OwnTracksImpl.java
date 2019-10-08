package net.yudichev.jiotty.connector.owntracks;

import net.yudichev.jiotty.common.async.DispatchedConsumer;
import net.yudichev.jiotty.common.lang.Closeable;
import net.yudichev.jiotty.common.lang.Json;
import net.yudichev.jiotty.connector.mqtt.Mqtt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

final class OwnTracksImpl implements OwnTracks {
    private static final Logger logger = LoggerFactory.getLogger(OwnTracksImpl.class);

    private final Mqtt mqtt;

    @Inject
    OwnTracksImpl(Mqtt mqtt) {
        this.mqtt = checkNotNull(mqtt);
    }

    @Override
    public Closeable subscribeToTransitions(Consumer<OwnTracksUpdate<OwnTracksTransition>> handler, Executor executor) {
        return mqttSubscribe("owntracks/+/+/event", dispatched(parsed(orderProtected(handler), OwnTracksTransition.class), executor));
    }

    @Override
    public Closeable subscribeToLocationUpdates(Consumer<OwnTracksUpdate<OwnTrackLocationUpdate>> handler, Executor executor) {
        return mqttSubscribe("owntracks/+/+", dispatched(parsed(lwtIgnored(orderProtected(handler)), OwnTracksLocationUpdateOrLwt.class), executor));
    }

    @Override
    public CompletableFuture<Void> publishLocationUpdateRequest(DeviceKey deviceKey) {
        return mqtt.publish(String.format("owntracks/%s/%s/cmd", deviceKey.userName(), deviceKey.deviceName()),
                "{\"_type\":\"cmd\",\"action\":\"reportLocation\"}");
    }

    private Closeable mqttSubscribe(String topic, Consumer<OwnTracksJsonMessage> consumer) {
        return mqtt.subscribe(topic, (theTopic, data) -> {
            logger.debug("IN {}: {}", theTopic, data);
            DeviceKey deviceKey = parseTopic(theTopic);
            consumer.accept(OwnTracksJsonMessage.of(deviceKey, data));
        });
    }

    private static DeviceKey parseTopic(String theTopic) {
        String[] topicParts = theTopic.split("/");
        checkState(topicParts.length >= 3,
                "owntracks topic format unrecognized: %s, should have at least 3 parts but had %s", theTopic, topicParts.length);
        return DeviceKey.of(topicParts[1], topicParts[2]);
    }

    private static Consumer<OwnTracksUpdate<OwnTracksLocationUpdateOrLwt>> lwtIgnored(Consumer<OwnTracksUpdate<OwnTrackLocationUpdate>> handler) {
        return updateOrLwt -> updateOrLwt.payload().asLocationUpdate().ifPresent(ownTrackLocationUpdate ->
                handler.accept(OwnTracksUpdate.of(updateOrLwt.deviceKey(), ownTrackLocationUpdate)));
    }

    private static <T extends HasFixTimestamp> Consumer<OwnTracksUpdate<T>> orderProtected(Consumer<OwnTracksUpdate<T>> delegate) {
        return new OrderProtectingConsumer<>(delegate);
    }

    private static <T> Consumer<T> dispatched(Consumer<T> delegate, Executor executor) {
        return new DispatchedConsumer<>(delegate, executor);
    }

    private static <T> Consumer<OwnTracksJsonMessage> parsed(Consumer<OwnTracksUpdate<T>> handler, Class<T> type) {
        return ownTracksJsonMessage -> handler.accept(OwnTracksUpdate.of(ownTracksJsonMessage.deviceKey(), Json.parse(ownTracksJsonMessage.payload(), type)));
    }

    private static class OrderProtectingConsumer<T extends HasFixTimestamp> implements Consumer<OwnTracksUpdate<T>> {
        private final Consumer<OwnTracksUpdate<T>> delegate;
        private long latestTimestamp = Long.MIN_VALUE;

        OrderProtectingConsumer(Consumer<OwnTracksUpdate<T>> delegate) {
            this.delegate = checkNotNull(delegate);
        }

        @Override
        public void accept(OwnTracksUpdate<T> t) {
            long timestamp = t.payload().fixTimestampSeconds();
            if (timestamp > latestTimestamp) {
                latestTimestamp = timestamp;
                delegate.accept(t);
            } else {
                logger.debug("Ignoring out-of-order message {}, latest timestamp was {}", t, latestTimestamp);
            }
        }
    }
}
