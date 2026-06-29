package org.example.dto;

public record AiActionResponse(
        ActionType action,
        OperationType operation,
        String target,
        String groupBy,
        String metric,
        GeminiFilters filters,
        Integer limit,
        Long entityId,
        String entityName,
        String status,
        String deviceType,
        String alarmType,
        String severity,
        String description
) {
    public AiActionResponse(ActionType action,
                            OperationType operation,
                            String target,
                            String groupBy,
                            String metric,
                            GeminiFilters filters,
                            Integer limit) {
        this(action, operation, target, groupBy, metric, filters, limit, null, null, null, null, null, null, null);
    }

    public static AiActionResponse unknown() {
        return new AiActionResponse(
                ActionType.UNKNOWN,
                OperationType.UNKNOWN,
                null,
                null,
                null,
                new GeminiFilters(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
