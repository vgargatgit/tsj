package dev.tsj.compiler.ir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON serializer for IR debug output.
 */
public final class IrJsonPrinter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private IrJsonPrinter() {
    }

    public static String toJson(final IrProject irProject) {
        try {
            return OBJECT_MAPPER.writeValueAsString(irProject);
        } catch (final JsonProcessingException jsonProcessingException) {
            throw new IllegalStateException(
                    "Failed to serialize IR project: " + jsonProcessingException.getMessage(),
                    jsonProcessingException
            );
        }
    }
}
