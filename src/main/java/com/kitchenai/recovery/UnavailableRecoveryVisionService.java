package com.kitchenai.recovery;
import com.kitchenai.recovery.RecoveryContracts.Analysis;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
@Service
@Profile("!runware")
public class UnavailableRecoveryVisionService implements RecoveryVisionService {
  @Override public Analysis analyze(byte[] image, String mimeType, String context) {
    throw new IllegalStateException("Vision provider is not configured");
  }
  @Override public boolean available() { return false; }
}