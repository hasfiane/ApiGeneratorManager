package experiment.ab;

import bench.entity.Customer;
import bench.types.OrdersId;
import dev.typebridge.api.AdaptationScope;

@AdaptationScope
public final class StrongCrossIdRejected {
    static Customer misuseOrderIdAsCustomerId(OrdersId orderId) {
        // MUST fail even with TypeBridge: OrdersId is not raw UUID and cannot become CustomerId.
        return Customer.builder().id(orderId).name("Ada").build();
    }
}
