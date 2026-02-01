package com.cuenti.homebanking.repository;

import com.cuenti.homebanking.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(String symbol, String name);
}
