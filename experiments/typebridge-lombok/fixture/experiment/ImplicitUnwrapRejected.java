package experiment;

import dev.typebridge.api.AdaptationScope;
import dev.typebridge.api.StrongType;

import java.util.UUID;

public final class ImplicitUnwrapRejected {
    @StrongType
    static final class CustomerId {
        private final UUID value;
        CustomerId(UUID value) { this.value = value; }
        UUID value() { return value; }
    }

    static final class RawSink {
        RawSink customerId(UUID value) { return this; }
    }

    @AdaptationScope
    static final class Mapper {
        void map(CustomerId id) {
            new RawSink().customerId(id); // MUST remain a compile error: strong -> raw is explicit.
        }
    }
}
