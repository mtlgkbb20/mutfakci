package com.kitchenai.ai;

import com.kitchenai.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.kitchenai.ai.RecipeContracts.ComponentPlan;
import static com.kitchenai.ai.RecipeContracts.RecipeComponentGenerationResponse;
import static com.kitchenai.ai.RecipeContracts.RecipeDecompositionResponse;

public final class RecipePipelineValidator {
    private RecipePipelineValidator() {
    }

    public static RecipeDecompositionResponse decomposition(RecipeDecompositionResponse plan) {
        if (plan == null || plan.components() == null || plan.components().isEmpty()
                || plan.components().size() > 6 || plan.dishTitle() == null
                || plan.dishTitle().isBlank()) {
            fail("Recipe decomposition is invalid");
        }

        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (ComponentPlan component : plan.components()) {
            if (component == null || component.key() == null || component.key().isBlank()
                    || !keys.add(component.key()) || component.name() == null
                    || component.name().isBlank() || component.order() < 1
                    || !orders.add(component.order())) {
                fail("Recipe decomposition is invalid");
            }
        }

        for (ComponentPlan component : plan.components()) {
            for (String dependency : dependencies(component)) {
                if (dependency.equals(component.key()) || !keys.contains(dependency)) {
                    fail("Recipe dependencies are invalid");
                }
            }
        }

        for (ComponentPlan component : plan.components()) {
            if (hasCycle(component.key(), plan.components(), new HashSet<>(), new HashSet<>())) {
                fail("Recipe dependencies are cyclic");
            }
        }
        return plan;
    }

    private static List<String> dependencies(ComponentPlan component) {
        return component.dependsOn() == null ? List.of() : component.dependsOn();
    }

    private static boolean hasCycle(String key, List<ComponentPlan> components,
                                    Set<String> visiting, Set<String> completed) {
        if (completed.contains(key)) {
            return false;
        }
        if (!visiting.add(key)) {
            return true;
        }

        ComponentPlan component = components.stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElse(null);
        if (component != null) {
            for (String dependency : dependencies(component)) {
                if (hasCycle(dependency, components, visiting, completed)) {
                    return true;
                }
            }
        }
        visiting.remove(key);
        completed.add(key);
        return false;
    }

    public static RecipeComponentGenerationResponse component(
            RecipeComponentGenerationResponse component, String expectedKey) {
        if (component == null || !expectedKey.equals(component.componentKey())
                || component.title() == null || component.title().isBlank()
                || component.ingredients() == null || component.ingredients().isEmpty()
                || component.instructions() == null || component.instructions().isEmpty()) {
            fail("Recipe component is invalid");
        }

        for (var ingredient : component.ingredients()) {
            if (ingredient.quantity() <= 0 || ingredient.name() == null
                    || ingredient.name().isBlank() || ingredient.unit() == null
                    || ingredient.unit().isBlank()) {
                fail("Recipe ingredient is invalid");
            }
        }

        int expectedOrder = 1;
        for (var instruction : component.instructions()) {
            if (instruction.order() != expectedOrder++ || instruction.instruction() == null
                    || instruction.instruction().isBlank()) {
                fail("Recipe instructions are invalid");
            }
        }
        return component;
    }

    private static void fail(String message) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, message);
    }
}
