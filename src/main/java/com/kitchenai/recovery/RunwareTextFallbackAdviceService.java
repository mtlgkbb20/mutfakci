package com.kitchenai.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@Profile("runware")
public class RunwareTextFallbackAdviceService implements TextFallbackAdviceService {
  private static final Logger log=LoggerFactory.getLogger(RunwareTextFallbackAdviceService.class);
  private static final String URL="https://api.runware.ai/v1";
  private final ObjectMapper json;
  private final HttpClient http;
  private final String apiKey;
  private final String model;

  public RunwareTextFallbackAdviceService(ObjectMapper json,Environment env){
    this.json=json;
    this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.apiKey=env.getProperty("app.ai.runware.api-key","");
    this.model=env.getProperty("app.ai.runware.text-fallback-model","deepseek:v4.1@flash");
  }

  @Override public Advice suggest(String recipeName,String problemDescription){
    if(apiKey.isBlank()||model.isBlank()) throw new IllegalStateException("Text fallback provider is not configured");
    try{
      String prompt="""
        You are a careful cooking troubleshooting assistant. The user cannot provide a photo.
        Give a concise, practical suggestion using ONLY the recipe name and the described problem.
        Do not claim to have seen the food. Do not assign visual scores or pretend certainty.
        Do not invent ingredients, quantities, temperatures, or timings. If a safe correction
        depends on missing details, say what detail is missing and give only a conservative
        next step. Never infer food safety or tell the user food is safe to eat.
        Respond in Turkish and return ONLY valid JSON with exactly these string fields:
        {"advice":"...","caution":"..."}
        Keep advice under 700 characters and caution under 300 characters.
        Recipe name: %s
        Problem: %s
        """.formatted(recipeName,problemDescription);
      var task=json.createObjectNode();
      task.put("taskType","textInference");
      task.put("taskUUID",UUID.randomUUID().toString());
      task.put("model",model);
      task.putArray("messages").addObject().put("role","user").put("content",prompt);
      task.putObject("settings").put("maxTokens",350).put("temperature",0.2);
      HttpRequest request=HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(45))
          .header("Authorization","Bearer "+apiKey).header("Content-Type","application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(json.createArrayNode().add(task))))
          .build();
      HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
      JsonNode root=json.readTree(response.body());
      if(response.statusCode()/100!=2||root.has("errors"))
        throw new IllegalStateException("Runware text fallback request failed with HTTP "+response.statusCode());
      String content=root.path("data").path(0).path("text").asText("").trim();
      if(content.isBlank()) throw new IllegalStateException("Runware returned an empty text fallback response");
      JsonNode output=json.readTree(content);
      Set<String> fields=new HashSet<>();
      output.fieldNames().forEachRemaining(fields::add);
      if(!fields.equals(Set.of("advice","caution"))
          ||!output.path("advice").isTextual()||!output.path("caution").isTextual())
        throw new IllegalStateException("Text fallback response did not match its contract");
      String advice=output.path("advice").asText().trim();
      String caution=output.path("caution").asText().trim();
      if(advice.isBlank()||advice.length()>700||caution.length()>300)
        throw new IllegalStateException("Text fallback response exceeded its contract");
      return new Advice(advice,caution);
    }catch(Exception e){
      log.error("Runware text-only recovery failed: model={}, exceptionType={}, message={}",
          model,e.getClass().getSimpleName(),e.getMessage(),e);
      throw new IllegalStateException("Text-only recovery advice could not be generated",e);
    }
  }
}
