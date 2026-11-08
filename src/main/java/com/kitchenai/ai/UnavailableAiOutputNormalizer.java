package com.kitchenai.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!runware")
public class UnavailableAiOutputNormalizer implements AiOutputNormalizer {
  @Override
  public String normalize(String operation, String rawOutput, String expectedSchema, String rules) {
    throw new IllegalStateException("AI output normalizer is unavailable");
  }

  @Override
  public boolean available() {
    return false;
  }
}
