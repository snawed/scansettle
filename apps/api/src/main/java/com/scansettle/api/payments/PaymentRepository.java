package com.scansettle.api.payments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link JpaSpecificationExecutor} backs the optional-filter transaction search
 * (docs/api.md) — predicates are only added for filters actually supplied. An
 * earlier {@code (:param is null or ...)} JPQL version hit a genuine PostgreSQL/JDBC
 * bug ("could not determine data type of parameter") when a nullable bind parameter
 * appears only inside an `is null or` comparison with no other type context; building
 * the query dynamically avoids the whole class of problem rather than working around it.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<Payment> findByPaymentLinkId(UUID paymentLinkId);

    Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    @Query("select coalesce(sum(p.amountMinorUnits), 0) from Payment p " +
            "where p.merchantId = :merchantId and p.state = 'PAYMENT_CONFIRMED' and p.createdAt >= :since")
    long sumConfirmedAmountSince(@Param("merchantId") UUID merchantId, @Param("since") Instant since);

    long countByMerchantIdAndStateAndCreatedAtGreaterThanEqual(UUID merchantId, PaymentState state, Instant since);

    long countByMerchantIdAndStateNotInAndCreatedAtGreaterThanEqual(
            UUID merchantId, List<PaymentState> terminalStates, Instant since);
}
