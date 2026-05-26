package com.attendance.common.uuid;

import com.github.f4b6a3.uuid.UuidCreator;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UuidV7 {

    private UuidV7() {
    }

    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    public static byte[] toBytes(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID byte array must be exactly 16 bytes, was " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long msb = buffer.getLong();
        long lsb = buffer.getLong();
        return new UUID(msb, lsb);
    }
}
