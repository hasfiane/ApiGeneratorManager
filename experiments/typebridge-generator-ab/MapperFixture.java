package experiment.ab;

import bench.entity.Customer;
import dev.typebridge.api.AdaptationScope;

import java.util.UUID;

@AdaptationScope
public final class MapperFixture {
    static Customer map(UUID raw) {
        // Invalid Java without TypeBridge once Customer.id is strongly typed.
        return Customer.builder().id(raw).name("Ada").build();
    }

    public static void main(String[] args) {
        UUID raw = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Customer customer = map(raw);
        // Domain -> boundary remains explicit by design.
        System.out.print(customer.getId().value());
    }
}
