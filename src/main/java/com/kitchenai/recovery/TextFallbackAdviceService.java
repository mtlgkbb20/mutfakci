package com.kitchenai.recovery;

public interface TextFallbackAdviceService {
  record Advice(String advice,String caution) {}
  Advice suggest(String recipeName,String problemDescription);
}
