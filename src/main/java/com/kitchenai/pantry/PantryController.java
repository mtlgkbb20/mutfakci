package com.kitchenai.pantry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.common.ApiException;
import com.kitchenai.common.IngredientNames;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pantry")
public class PantryController {
  private static final int MATCH_PAGE_SIZE = 3;
  private static final int MATCH_FETCH_SIZE = MATCH_PAGE_SIZE + 1;
  private static final Set<String> MATCH_CATEGORIES = Set.of("MEAL", "DESSERT", "DRINK");

  private final PantryItemRepository items;
  private final PantryMovementRepository movements;
  private final PantryOperationRepository operations;
  private final PantryRecipeMatchRepository recipeMatches;
  private final ObjectMapper objectMapper;

  public PantryController(PantryItemRepository items, PantryMovementRepository movements,
      PantryOperationRepository operations, PantryRecipeMatchRepository recipeMatches,
      ObjectMapper objectMapper) {
    this.items=items; this.movements=movements; this.operations=operations;
    this.recipeMatches=recipeMatches; this.objectMapper=objectMapper;
  }

  public record ItemRequest(@NotBlank @Size(max=160) String name,@NotNull @DecimalMin("0.001") @Digits(integer=9,fraction=3) BigDecimal quantity,@NotBlank String unit,String category,LocalDate expiryDate) {}
  public record ItemView(UUID id,String name,String category,BigDecimal quantity,String unit,LocalDate expiryDate,boolean expired,boolean expiringSoon,long version) {}
  public record MovementView(UUID id,UUID pantryItemId,String movementType,BigDecimal quantityDelta,String unit,String reason,Instant createdAt) {}
  public record ConsumptionLine(@NotNull UUID pantryItemId,@NotNull @DecimalMin("0.001") BigDecimal quantity,@NotBlank String unit) {}
  public record ConsumeRequest(@NotBlank @Size(max=100) String operationKey,@NotEmpty List<@Valid ConsumptionLine> items) {}
  public record MatchView(String recipeId,String title,int servings,double coverage,List<Map<String,Object>> available,List<Map<String,Object>> missing) {}
  public record MatchPage(String category,int page,int size,boolean hasMore,List<MatchView> recipes) {}

  @GetMapping("/items") public List<ItemView> list(@AuthenticationPrincipal UUID userId){return items.findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(userId).stream().map(this::view).toList();}
  @PostMapping("/items") @Transactional public ItemView create(@Valid @RequestBody ItemRequest r,@AuthenticationPrincipal UUID userId){
    String unit=unit(r.unit()); PantryItem item=items.save(new PantryItem(userId,r.name().trim(),IngredientNames.normalize(r.name()),category(r.category()),r.quantity(),unit,r.expiryDate()));
    movements.save(new PantryMovement(userId,item.getId(),"ADD",r.quantity(),unit,"Initial stock",null)); return view(item);
  }
  @PatchMapping("/items/{id}") @Transactional public ItemView update(@PathVariable UUID id,@Valid @RequestBody ItemRequest r,@AuthenticationPrincipal UUID userId){
    PantryItem item=owned(id,userId); String newUnit=unit(r.unit());
    if(!newUnit.equals(item.getUnit()) && item.getQuantity().signum()>0) throw bad("Birim değişikliği için önce mevcut stoğu tüketin veya ayrı ürün oluşturun.");
    BigDecimal delta=r.quantity().subtract(item.getQuantity());
    item.update(r.name().trim(),IngredientNames.normalize(r.name()),category(r.category()),r.quantity(),newUnit,r.expiryDate());
    if(delta.signum()!=0) movements.save(new PantryMovement(userId,id,"ADJUST",delta,newUnit,"Manual stock adjustment",null));
    return view(items.save(item));
  }
  @DeleteMapping("/items/{id}") @Transactional public Map<String,String> archive(@PathVariable UUID id,@AuthenticationPrincipal UUID userId){PantryItem item=owned(id,userId);item.archive();items.save(item);return Map.of("status","ARCHIVED");}
  @GetMapping("/movements") public List<MovementView> history(@RequestParam(required=false) UUID itemId,@AuthenticationPrincipal UUID userId){
    if(itemId!=null) owned(itemId,userId);
    return (itemId==null?movements.findTop100ByUserIdOrderByCreatedAtDesc(userId):movements.findTop100ByUserIdAndPantryItemIdOrderByCreatedAtDesc(userId,itemId)).stream().map(m->new MovementView(m.getId(),m.getPantryItemId(),m.getMovementType(),m.getQuantityDelta(),m.getUnit(),m.getReason(),m.getCreatedAt())).toList();
  }
  @GetMapping("/expiring") public List<ItemView> expiring(@RequestParam(defaultValue="7") @Min(0) @Max(90) int withinDays,@AuthenticationPrincipal UUID userId){
    LocalDate today=LocalDate.now(); List<PantryItem> result=new ArrayList<>(items.findByUserIdAndArchivedFalseAndExpiryDateIsNotNullAndExpiryDateLessThanEqualOrderByExpiryDateAsc(userId,today.plusDays(withinDays)));
    return result.stream().map(this::view).toList();
  }

  @GetMapping("/recipe-matches")
  public MatchPage recipeMatches(@RequestParam(defaultValue="MEAL") String category,
      @RequestParam(defaultValue="0") @Min(0) @Max(1000) int page,
      @AuthenticationPrincipal UUID userId) {
    return findRecipeMatches(category, page, userId);
  }

  /** Backward-compatible entry point for older mobile builds. */
  @PostMapping("/recipe-matches")
  public List<MatchView> legacyRecipeMatches(@AuthenticationPrincipal UUID userId) {
    return findRecipeMatches("MEAL", 0, userId).recipes();
  }

  private MatchPage findRecipeMatches(String category, int page, UUID userId) {
    if (page < 0 || page > 1000) throw bad("Sayfa numarası 0 ile 1000 arasında olmalıdır.");
    String normalizedCategory=category==null?"":category.trim().toUpperCase(Locale.ROOT);
    if(!MATCH_CATEGORIES.contains(normalizedCategory)) throw bad("Kategori MEAL, DESSERT veya DRINK olmalıdır.");
    int offset=Math.multiplyExact(page,MATCH_PAGE_SIZE);
    List<PantryRecipeMatchProjection> rows=recipeMatches.findMatches(userId,normalizedCategory,MATCH_FETCH_SIZE,offset);
    boolean hasMore=rows.size()>MATCH_PAGE_SIZE;
    List<MatchView> result=rows.stream().limit(MATCH_PAGE_SIZE).map(this::toMatchView).toList();
    return new MatchPage(normalizedCategory,page,MATCH_PAGE_SIZE,hasMore,result);
  }

  private MatchView toMatchView(PantryRecipeMatchProjection row) {
    try {
      List<Map<String,Object>> available=objectMapper.readValue(row.getAvailableJson(),new TypeReference<List<Map<String,Object>>>() {});
      List<Map<String,Object>> missing=objectMapper.readValue(row.getMissingJson(),new TypeReference<List<Map<String,Object>>>() {});
      return new MatchView(row.getRecipeId(),row.getTitle(),row.getServings(),
          row.getCoverage()==null?0d:row.getCoverage(),available,missing);
    } catch(Exception e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"Recipe match result could not be read");
    }
  }

  @PostMapping("/recipe-consumptions") @Transactional public Map<String,Object> consume(@Valid @RequestBody ConsumeRequest r,@AuthenticationPrincipal UUID userId){
    if(operations.existsByUserIdAndOperationKey(userId,r.operationKey())) return Map.of("status","ALREADY_APPLIED","operationKey",r.operationKey());
    Map<UUID,PantryItem> locked=new LinkedHashMap<>();
    for(ConsumptionLine line:r.items()){PantryItem item=items.lockOwned(line.pantryItemId(),userId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Pantry item not found")); locked.put(item.getId(),item);}
    if(operations.existsByUserIdAndOperationKey(userId,r.operationKey())) return Map.of("status","ALREADY_APPLIED","operationKey",r.operationKey());
    record Deduction(PantryItem item,BigDecimal amount) {}
    Map<UUID,BigDecimal> totals=new LinkedHashMap<>();
    for(ConsumptionLine line:r.items()){
      PantryItem item=locked.get(line.pantryItemId()); String from=unit(line.unit());
      if(!compatible(item.getUnit(),from)) throw bad("Birimler birbiriyle dönüştürülemiyor.");
      totals.merge(item.getId(),convert(line.quantity(),from,item.getUnit()),BigDecimal::add);
    }
    List<Deduction> deductions=new ArrayList<>();
    for(var entry:totals.entrySet()){
      PantryItem item=locked.get(entry.getKey()); BigDecimal amount=entry.getValue();
      if(item.getQuantity().compareTo(amount)<0) throw bad("Yetersiz stok: "+item.getName());
      deductions.add(new Deduction(item,amount));
    }
    operations.save(new PantryOperation(userId,r.operationKey()));
    for(Deduction d:deductions){d.item().adjust(d.amount().negate());items.save(d.item());movements.save(new PantryMovement(userId,d.item().getId(),"CONSUME",d.amount().negate(),d.item().getUnit(),"Confirmed recipe consumption",r.operationKey()));}
    return Map.of("status","APPLIED","operationKey",r.operationKey(),"deductedLines",deductions.size());
  }
  private PantryItem owned(UUID id,UUID user){return items.findByIdAndUserIdAndArchivedFalse(id,user).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Pantry item not found"));}
  private ItemView view(PantryItem p){LocalDate today=LocalDate.now();boolean expired=p.getExpiryDate()!=null&&p.getExpiryDate().isBefore(today);boolean soon=p.getExpiryDate()!=null&&!expired&&!p.getExpiryDate().isAfter(today.plusDays(7));return new ItemView(p.getId(),p.getName(),p.getCategory(),p.getQuantity(),p.getUnit(),p.getExpiryDate(),expired,soon,p.getVersion());}
  private static String category(String c){return c==null?"":c.trim();}
  private static String unit(String u){String x=u.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");if(x.isBlank()||x.length()>20)throw bad("Geçersiz birim.");return x;}
  private static boolean compatible(String a,String b){return dimension(a).equals(dimension(b));}
  private static String dimension(String u){if(u.equals("g")||u.equals("kg"))return "MASS";if(u.equals("ml")||u.equals("l"))return "VOLUME";if(Set.of("piece","adet","pcs").contains(u))return "COUNT";return "CUSTOM:"+u;}
  private static BigDecimal convert(BigDecimal q,String from,String to){if(!compatible(from,to))return BigDecimal.valueOf(-1);if(from.equals(to))return q; if(from.equals("kg")&&to.equals("g"))return q.multiply(BigDecimal.valueOf(1000));if(from.equals("g")&&to.equals("kg"))return q.divide(BigDecimal.valueOf(1000),3,RoundingMode.HALF_UP);if(from.equals("l")&&to.equals("ml"))return q.multiply(BigDecimal.valueOf(1000));if(from.equals("ml")&&to.equals("l"))return q.divide(BigDecimal.valueOf(1000),3,RoundingMode.HALF_UP);return q;}
  private static ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,message);}
}
