package experiment.ab;

import bench.entity.Customer;
import dev.typebridge.api.AdaptationScope;

import java.util.UUID;

@AdaptationScope
public final class MapperFixture {
    private MapperFixture() {}

    public static Customer map(UUID raw) {
        // Exact same mapper source in both variants:
        // baseline accepts UUID directly; strong mode needs TypeBridge for CustomerId.
        return Customer.builder().id(raw).name("Ada").build();
    }
}
