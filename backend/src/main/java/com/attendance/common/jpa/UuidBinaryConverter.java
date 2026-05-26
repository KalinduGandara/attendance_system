package com.attendance.common.jpa;

import com.attendance.common.uuid.UuidV7;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

@Converter(autoApply = false)
public class UuidBinaryConverter implements AttributeConverter<UUID, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(UUID uuid) {
        return UuidV7.toBytes(uuid);
    }

    @Override
    public UUID convertToEntityAttribute(byte[] dbData) {
        return UuidV7.fromBytes(dbData);
    }
}
