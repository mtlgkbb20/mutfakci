package com.kitchenai.shopping;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {
  List<ShoppingList> findTop50ByUserIdOrderByUpdatedAtDesc(UUID userId);
  Optional<ShoppingList> findByIdAndUserId(UUID id, UUID userId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select l from ShoppingList l where l.id=:id and l.userId=:userId")
  Optional<ShoppingList> lockOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}

interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {
  List<ShoppingListItem> findByListIdOrderByCategoryAscNameAsc(UUID listId);
  Optional<ShoppingListItem> findByIdAndListId(UUID id, UUID listId);
}
