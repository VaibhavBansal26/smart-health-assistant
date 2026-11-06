package com.healthcare.app.risk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.app.crypto.FieldEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/** RiskInputs <-> encrypted JSON text. The raw health measurements never sit in the database in the clear. */
@Converter
@Component
public class EncryptedRiskInputsConverter implements AttributeConverter<RiskInputs, String> {

    private final FieldEncryptor encryptor;
    private final ObjectMapper json;

    public EncryptedRiskInputsConverter(FieldEncryptor encryptor, ObjectMapper json) {
        this.encryptor = encryptor;
        this.json = json;
    }

    @Override
    public String convertToDatabaseColumn(RiskInputs attribute) {
        try {
            return attribute == null ? null : encryptor.encrypt(json.writeValueAsString(attribute));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public RiskInputs convertToEntityAttribute(String dbData) {
        try {
            return dbData == null ? null : json.readValue(encryptor.decrypt(dbData), RiskInputs.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
