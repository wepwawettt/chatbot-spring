package org.example.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum OperationType {
    LIST,
    COUNT,
    DETAIL,
    GROUP_BY,
    TOP_N,
    AVERAGE,
    SUM,
    MIN,
    MAX,
    COMPARE,
    UPDATE_STATUS,
    CREATE,
    RESOLVE,
    @JsonEnumDefaultValue
    UNKNOWN
}
