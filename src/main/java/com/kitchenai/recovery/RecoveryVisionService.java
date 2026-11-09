package com.kitchenai.recovery;
import com.kitchenai.recovery.RecoveryContracts.Analysis;
public interface RecoveryVisionService {
  Analysis analyze(byte[] image, String mimeType, String context);
  boolean available();
}