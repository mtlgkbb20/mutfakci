package com.kitchenai.recovery;
public final class RecoveryImageTrustPolicy {
  private RecoveryImageTrustPolicy() {}
  public static boolean accepts(int score) {
    if(score<0||score>100) throw new IllegalArgumentException("Image trust score must be between 0 and 100");
    return score>=60;
  }
}