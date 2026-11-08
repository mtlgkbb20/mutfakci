package com.kitchenai.ai;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.kitchenai.ai.RecipeContracts.*;

class RecipePipelineValidatorTest {
    @Test void acceptsSingleComponent() {
        var p=new RecipeDecompositionResponse("Kek",4,List.of(new ComponentPlan("cake","Kek","Hamur",1,List.of())),false);
        assertSame(p,RecipePipelineValidator.decomposition(p));
    }
    @Test void rejectsDependencyCycles() {
        var p=new RecipeDecompositionResponse("Tatlı",4,List.of(new ComponentPlan("a","A","",1,List.of("b")),new ComponentPlan("b","B","",2,List.of("a"))),true);
        assertThrows(RuntimeException.class,()->RecipePipelineValidator.decomposition(p));
    }
    @Test void rejectsMismatchedComponentKey() {
        var c=new RecipeComponentGenerationResponse("cream","Krema",List.of(new ComponentIngredient("Süt",1,"l","")),List.of(new ComponentInstruction(1,"Isıt","Kaynamamalı",null,60)));
        assertThrows(RuntimeException.class,()->RecipePipelineValidator.component(c,"choux"));
    }
}
