package net.yudichev.jiotty.connector.miele;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.yudichev.jiotty.common.lang.PublicImmutablesStyle;
import org.immutables.value.Value.Immutable;

@Immutable
@PublicImmutablesStyle
@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
interface BaseDeviceState extends MieleEvent {
    @JsonProperty("ProgramID")
    StateValue program();

    StateValue status();

    @JsonProperty("remoteEnable")
    RemoteControlStatus remoteControlStatus();
}
