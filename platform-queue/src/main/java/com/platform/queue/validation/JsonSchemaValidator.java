package com.platform.queue.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class JsonSchemaValidator implements InitializingBean {

  private Schema envelopeSchema;

  @Override
  public void afterPropertiesSet() throws IOException {
    try (InputStream in =
        Objects.requireNonNull(
            JsonSchemaValidator.class.getResourceAsStream("/schemas/envelope.json"),
            "classpath resource /schemas/envelope.json missing")) {
      JSONObject rawSchema = new JSONObject(new JSONTokener(in));
      this.envelopeSchema = SchemaLoader.load(rawSchema);
    }
  }

  public void validateJSONObject(JSONObject payload) throws ValidationException {
    envelopeSchema.validate(payload);
  }

  public void validate(String json) {
    validateJSONObject(new JSONObject(json));
  }
}
