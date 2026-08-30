package com.scansettle.api.merchant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Page<Merchant> findByTradingNameContainingIgnoreCase(String tradingName, Pageable pageable);
}
