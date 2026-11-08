package com.kitchenai.ai;

/**
 * Converts an untrusted model response into a requested JSON shape.
 * The caller must still validate the normalized output deterministically.
 */
public interface AiOutputNormalizer {
  String normalize(String operation, String rawOutput, String expectedSchema, String rules);
  boolean available();
}
