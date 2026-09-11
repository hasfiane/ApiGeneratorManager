package experiment;

import dev.typebridge.api.AdaptationScope;
import dev.typebridge.api.StrongType;
import lombok.Builder;

import java.util.UUID;

@StrongType(unwrap = "value")
record CustomerId(UUID value) {}

@Builder
class Order {
    CustomerId customerId;
}

@AdaptationScope
public class LombokFixture {
    static Order map(UUID raw) {
        // Invalid Java without TypeBridge: Lombok generates customerId(CustomerId).
        return Order.builder().customerId(raw).build();
    }

    public static void main(String[] args) {
        UUID raw = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Order order = map(raw);
        System.out.print(order.customerId.value());
    }
}
