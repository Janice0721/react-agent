package com.reactagent.core.msg.block;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ToolResultState {
    RUNNING, SUCCESS, ERROR, INTERRUPTED, DENIED
}