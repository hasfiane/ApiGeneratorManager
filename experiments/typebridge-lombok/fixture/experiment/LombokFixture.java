package experiment;

import dev.typebridge.api.AdaptationScope;
import dev.typebridge.api.StrongType;
import lombok.Builder;

import java.util.UUID;

@StrongType
final class CustomerId {
    private final UUID value;

    CustomerId(UUID value) {
        this.value = value;
    }

    UUID value() {
        return value;
    }
}

@Builder
class Order {
    CustomerId customerId;
}

@AdaptationScope
public class LombokFixture {
    static Order map(UUID raw) {
        // Invalid Java without TypeBridge: Lombok generates customerId(CustomerId).
        // The integration engine discovers both sides; neither name is hard-coded.
        return Order.builder().customerId(raw).build();
    }

    public static void main(String[] args) {
        UUID raw = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Order order = map(raw);
        // Boundary exit stays explicit by design.
        System.out.print(order.customerId.value());
    }
}
