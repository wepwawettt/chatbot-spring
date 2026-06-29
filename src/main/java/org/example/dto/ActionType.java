package org.example.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum ActionType {
    DEVICE_QUERY,
    ALARM_QUERY,
    DEVICE_COMMAND,
    ALARM_COMMAND,
    CALCULATION,
    GENERAL_QUESTION,
    @JsonEnumDefaultValue
    UNKNOWN
}
