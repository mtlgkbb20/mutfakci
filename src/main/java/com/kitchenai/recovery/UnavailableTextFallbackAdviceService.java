package com.kitchenai.recovery;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!runware")
public class UnavailableTextFallbackAdviceService implements TextFallbackAdviceService {
  @Override public Advice suggest(String recipeName,String problemDescription) {
    throw new IllegalStateException("Text fallback provider is not configured");
  }
}
