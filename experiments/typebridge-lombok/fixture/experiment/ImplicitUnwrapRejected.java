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

    static void raw(UUID value) {}

    @AdaptationScope
    static final class Mapper {
        void map(CustomerId id) {
            raw(id); // MUST remain a compile error: strong -> raw is explicit.
        }
    }
}
