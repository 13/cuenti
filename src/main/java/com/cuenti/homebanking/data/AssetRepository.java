package com.cuenti.homebanking.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByUser(User user);
    List<Asset> findByUserAndSymbolContainingIgnoreCaseOrUserAndNameContainingIgnoreCase(
        User user1, String symbol, User user2, String name);
    Optional<Asset> findByIdAndUser(Long id, User user);
}
