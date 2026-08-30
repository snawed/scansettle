package com.scansettle.api.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PricingPlanRepository extends JpaRepository<PricingPlan, UUID> {
    Optional<PricingPlan> findByCode(PlanCode code);
}
