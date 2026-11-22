package com.kitchenai.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IngredientNamesTest {
  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "Domates salçası|domates salcasi",
      "domates-salçası|domates salcasi",
      "Domates_Salçası|domates salcasi",
      "  DOMATES   SALÇASI  |domates salcasi",
      "Domates, salçası!|domates salcasi",
      "Iğdır|igdir",
      "İncir|incir",
      "Zeytinyağı (sızma)|zeytinyagi sizma",
      "Un %00|un 00",
      "Süt|sut"})
  void normalizesTurkishPunctuatedNamesToTheAliasKey(String input, String expected) {
    assertEquals(expected, IngredientNames.normalize(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "---", "!!!"})
  void returnsBlankForNamesWithoutLettersOrDigits(String input) {
    assertEquals("", IngredientNames.normalize(input));
  }
}
