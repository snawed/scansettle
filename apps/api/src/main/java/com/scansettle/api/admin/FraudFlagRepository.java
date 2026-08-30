package com.scansettle.api.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudFlagRepository extends JpaRepository<FraudFlag, UUID> {

    List<FraudFlag> findAllByOrderByRaisedAtDesc();

    List<FraudFlag> findByMerchantIdOrderByRaisedAtDesc(UUID merchantId);
}
