package com.cuenti.homebanking.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduledTransactionRepository extends JpaRepository<ScheduledTransaction, Long> {
    List<ScheduledTransaction> findByUser(User user);

    /**
     * Count scheduled transactions that reference a specific asset.
     */
    @Query("SELECT COUNT(st) FROM ScheduledTransaction st WHERE st.asset = :asset")
    long countByAsset(@Param("asset") Asset asset);

    /**
     * Update all scheduled transactions using a specific category to set category to null.
     */
    @Modifying
    @Query("UPDATE ScheduledTransaction st SET st.category = null WHERE st.category.id = :categoryId")
    int clearCategoryReferences(@Param("categoryId") Long categoryId);
}
