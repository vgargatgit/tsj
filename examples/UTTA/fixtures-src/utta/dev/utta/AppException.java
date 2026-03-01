package dev.utta;

/**
 * Custom exception hierarchy for interop testing.
 */
public class AppException extends RuntimeException {
    private final int code;
    public AppException(String message, int code) { super(message); this.code = code; }
    public int getCode() { return code; }

    public static class ValidationException extends AppException {
        private final String field;
        public ValidationException(String field, String message) {
            super(message, 400);
            this.field = field;
        }
        public String getField() { return field; }
    }

    public static class NotFoundException extends AppException {
        public NotFoundException(String entity) {
            super(entity + " not found", 404);
        }
    }

    // Method that throws checked exception (wrapped)
    public static String riskyOperation(boolean shouldFail) {
        if (shouldFail) throw new ValidationException("email", "Invalid email");
        return "success";
    }

    // Method that throws different exception types
    public static String dispatch(int errorType) {
        switch (errorType) {
            case 1: throw new ValidationException("name", "Name required");
            case 2: throw new NotFoundException("User");
            case 3: throw new AppException("Server error", 500);
            default: return "ok";
        }
    }
}
