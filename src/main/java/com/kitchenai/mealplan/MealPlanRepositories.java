package com.kitchenai.mealplan;

import com.kitchenai.pantry.PantryItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {
  Optional<MealPlan> findByIdAndUserId(UUID id, UUID userId);
  Optional<MealPlan> findByUserIdAndWeekStart(UUID userId, java.time.LocalDate weekStart);
  List<MealPlan> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}

interface MealPlanEntryRepository extends JpaRepository<MealPlanEntry, UUID> {
  List<MealPlanEntry> findByPlanIdOrderByMealDateAscMealSlotAsc(UUID planId);
  Optional<MealPlanEntry> findByIdAndPlanId(UUID id, UUID planId);
}

interface MealPlanPantryRepository extends JpaRepository<PantryItem, UUID> {
  List<PantryItem> findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(UUID userId);
}
