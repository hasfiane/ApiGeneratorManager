package experiment.ab;

import bench.entity.Customer;

import java.util.UUID;

public final class BaselineCrossIdAccepted {
    static Customer misuseOrderIdAsCustomerId(UUID orderId) {
        // Baseline cannot distinguish an order UUID from a customer UUID.
        return Customer.builder().id(orderId).name("Ada").build();
    }
}
