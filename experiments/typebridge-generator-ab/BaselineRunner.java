package experiment.ab;

import bench.entity.Customer;

import java.util.UUID;

public final class BaselineRunner {
    public static void main(String[] args) {
        UUID raw = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Customer customer = MapperFixture.map(raw);
        System.out.print(customer.getId());
    }
}
