package net.yudichev.jiotty.connector.google.photos;

import com.google.common.base.MoreObjects;
import com.google.photos.types.proto.MediaItem;
import com.google.protobuf.Timestamp;

import java.time.Instant;

import static com.google.common.base.Preconditions.checkNotNull;

final class InternalGoogleMediaItem implements GoogleMediaItem {
    private final MediaItem mediaItem;

    InternalGoogleMediaItem(MediaItem mediaItem) {
        this.mediaItem = checkNotNull(mediaItem);
    }

    @Override
    public String getId() {
        return mediaItem.getId();
    }

    @Override
    public Instant getCreationTime() {
        Timestamp creationTime = mediaItem.getMediaMetadata().getCreationTime();
        return Instant.ofEpochSecond(creationTime.getSeconds(), creationTime.getNanos());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("mediaItem", mediaItem)
                .toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        InternalGoogleMediaItem anotherItem = (InternalGoogleMediaItem) obj;
        return mediaItem.getId().equals(anotherItem.mediaItem.getId());
    }

    @Override
    public int hashCode() {
        return mediaItem.getId().hashCode();
    }
}
