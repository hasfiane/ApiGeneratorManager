package experiment;

import dev.typebridge.api.AdaptationScope;
import dev.typebridge.api.StrongType;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Branch-only typed-domain experiment based on the real customers/orders schema
 * used by GeneratedApiEndToEndTest.
 */
public final class TypedDomainFixture {

    @StrongType
    public record CustomerId(Long value) {}

    @StrongType
    public record OrderId(Long value) {}

    @StrongType
    public record Money(BigDecimal value) {}

    @Builder
    public record CreateOrderCommand(
            CustomerId customerId,
            String publicRef,
            Money totalAmount
    ) {}

    public record RawOrderRow(Long customerId, String publicRef, BigDecimal totalAmount) {}

    @AdaptationScope
    static final class Mapper {
        CreateOrderCommand toDomain(Long customerId, String publicRef, BigDecimal totalAmount) {
            return CreateOrderCommand.builder()
                    .customerId(customerId)
                    .publicRef(publicRef)
                    .totalAmount(totalAmount)
                    .build();
        }

        RawOrderRow toPersistence(CreateOrderCommand command) {
            // Leaving the domain stays explicit by design.
            return new RawOrderRow(
                    command.customerId().value(),
                    command.publicRef(),
                    command.totalAmount().value()
            );
        }
    }

    public static void main(String[] args) {
        Mapper mapper = new Mapper();
        CreateOrderCommand domain = mapper.toDomain(41L, "ORD-001", new BigDecimal("42.50"));
        RawOrderRow raw = mapper.toPersistence(domain);
        System.out.print(
                domain.customerId().value()
                        + "|" + domain.publicRef()
                        + "|" + domain.totalAmount().value().toPlainString()
                        + "|" + raw.customerId()
                        + "|" + raw.totalAmount().toPlainString()
        );
    }
}
