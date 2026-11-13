package com.kitchenai.pantry;

import com.kitchenai.common.ApiException;
import com.kitchenai.recipe.RecipeCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.text.Normalizer;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pantry")
public class PantryController {
  private final PantryItemRepository items;
  private final PantryMovementRepository movements;
  private final RecipeCatalogService catalog;
  public PantryController(PantryItemRepository items,PantryMovementRepository movements,RecipeCatalogService catalog){this.items=items;this.movements=movements;this.catalog=catalog;}

  public record ItemRequest(@NotBlank @Size(max=160) String name,@NotNull @DecimalMin("0.001") @Digits(integer=9,fraction=3) BigDecimal quantity,@NotBlank String unit,String category,LocalDate expiryDate) {}
  public record ItemView(UUID id,String name,String category,BigDecimal quantity,String unit,LocalDate expiryDate,boolean expired,boolean expiringSoon,long version) {}
  public record MovementView(UUID id,UUID pantryItemId,String movementType,BigDecimal quantityDelta,String unit,String reason,Instant createdAt) {}
  public record ConsumptionLine(@NotNull UUID pantryItemId,@NotNull @DecimalMin("0.001") BigDecimal quantity,@NotBlank String unit) {}
  public record ConsumeRequest(@NotBlank @Size(max=100) String operationKey,@NotEmpty List<@Valid ConsumptionLine> items) {}
  public record MatchView(String recipeId,String title,int servings,double coverage,List<Map<String,Object>> available,List<Map<String,Object>> missing) {}

  @GetMapping("/items") public List<ItemView> list(@AuthenticationPrincipal UUID userId){return items.findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(userId).stream().map(this::view).toList();}
  @PostMapping("/items") @Transactional public ItemView create(@Valid @RequestBody ItemRequest r,@AuthenticationPrincipal UUID userId){
    String unit=unit(r.unit()); PantryItem item=items.save(new PantryItem(userId,r.name().trim(),normalize(r.name()),category(r.category()),r.quantity(),unit,r.expiryDate()));
    movements.save(new PantryMovement(userId,item.getId(),"ADD",r.quantity(),unit,"Initial stock",null)); return view(item);
  }
  @PatchMapping("/items/{id}") @Transactional public ItemView update(@PathVariable UUID id,@Valid @RequestBody ItemRequest r,@AuthenticationPrincipal UUID userId){
    PantryItem item=owned(id,userId); String newUnit=unit(r.unit());
    if(!newUnit.equals(item.getUnit()) && item.getQuantity().signum()>0) throw bad("Birim değişikliği için önce mevcut stoğu tüketin veya ayrı ürün oluşturun.");
    BigDecimal delta=r.quantity().subtract(item.getQuantity());
    item.update(r.name().trim(),normalize(r.name()),category(r.category()),r.quantity(),newUnit,r.expiryDate());
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
  @PostMapping("/recipe-matches") public List<MatchView> matches(@AuthenticationPrincipal UUID userId){
    List<PantryItem> stock=items.findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(userId);
    return catalog.all().stream().map(recipe->{
      List<Map<String,Object>> available=new ArrayList<>(),missing=new ArrayList<>(); int total=recipe.ingredients().size(),matched=0;
      for(var ingredient:recipe.ingredients()){
        String key=normalize(ingredient.name()); Optional<PantryItem> found=stock.stream().filter(p->p.getNormalizedName().equals(key)).findFirst();
        if(found.isPresent() && compatible(found.get().getUnit(),ingredient.unit()) && found.get().getQuantity().compareTo(convert(BigDecimal.valueOf(ingredient.quantity()),ingredient.unit(),found.get().getUnit()))>=0){
          matched++; available.add(Map.of("name",ingredient.name(),"pantryItemId",found.get().getId(),"required",ingredient.quantity(),"unit",ingredient.unit(),"stock",found.get().getQuantity(),"stockUnit",found.get().getUnit()));
        } else missing.add(Map.of("name",ingredient.name(),"required",ingredient.quantity(),"unit",ingredient.unit()));
      }
      return new MatchView(recipe.id(),recipe.title(),recipe.defaultServings(),total==0?0:(double)matched/total,available,missing);
    }).sorted(Comparator.comparingDouble(MatchView::coverage).reversed()).limit(20).toList();
  }
  @PostMapping("/recipe-consumptions") @Transactional public Map<String,Object> consume(@Valid @RequestBody ConsumeRequest r,@AuthenticationPrincipal UUID userId){
    Optional<PantryMovement> prior=movements.findByUserIdAndOperationKey(userId,r.operationKey());
    if(prior.isPresent()) return Map.of("status","ALREADY_APPLIED","operationKey",r.operationKey());
    Map<UUID,PantryItem> locked=new LinkedHashMap<>();
    for(ConsumptionLine line:r.items()){PantryItem item=items.lockOwned(line.pantryItemId(),userId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Pantry item not found")); locked.put(item.getId(),item);}
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
    for(Deduction d:deductions){d.item().adjust(d.amount().negate());items.save(d.item());movements.save(new PantryMovement(userId,d.item().getId(),"CONSUME",d.amount().negate(),d.item().getUnit(),"Confirmed recipe consumption",r.operationKey()));}
    return Map.of("status","APPLIED","operationKey",r.operationKey(),"deductedLines",deductions.size());
  }
  private PantryItem owned(UUID id,UUID user){return items.findByIdAndUserIdAndArchivedFalse(id,user).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Pantry item not found"));}
  private ItemView view(PantryItem p){LocalDate today=LocalDate.now();boolean expired=p.getExpiryDate()!=null&&p.getExpiryDate().isBefore(today);boolean soon=p.getExpiryDate()!=null&&!expired&&!p.getExpiryDate().isAfter(today.plusDays(7));return new ItemView(p.getId(),p.getName(),p.getCategory(),p.getQuantity(),p.getUnit(),p.getExpiryDate(),expired,soon,p.getVersion());}
  private static String normalize(String v){return Normalizer.normalize(v.trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^a-z0-9 ]","").replaceAll("\\s+"," ");}
  private static String category(String c){return c==null||c.isBlank()?"OTHER":c.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]","_");}
  private static String unit(String u){String x=u.trim().toLowerCase(Locale.ROOT);if(!Set.of("g","kg","ml","l","piece","pcs","adet").contains(x))throw bad("Desteklenmeyen birim. Kullan: g, kg, ml, l, piece.");return x.equals("pcs")||x.equals("adet")?"piece":x;}
  private static boolean compatible(String a,String b){return dimension(a).equals(dimension(b));}
  private static String dimension(String u){if(u.equals("g")||u.equals("kg"))return "MASS";if(u.equals("ml")||u.equals("l"))return "VOLUME";return "COUNT";}
  private static BigDecimal convert(BigDecimal q,String from,String to){if(!compatible(from,to))return BigDecimal.valueOf(-1);if(from.equals(to))return q; if(from.equals("kg")&&to.equals("g"))return q.multiply(BigDecimal.valueOf(1000));if(from.equals("g")&&to.equals("kg"))return q.divide(BigDecimal.valueOf(1000),3,RoundingMode.HALF_UP);if(from.equals("l")&&to.equals("ml"))return q.multiply(BigDecimal.valueOf(1000));if(from.equals("ml")&&to.equals("l"))return q.divide(BigDecimal.valueOf(1000),3,RoundingMode.HALF_UP);return q;}
  private static ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,message);}
}
