package com.reactagent.core.msg;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Role {
    USER,
    ASSISTANT,
    SYSTEM
}