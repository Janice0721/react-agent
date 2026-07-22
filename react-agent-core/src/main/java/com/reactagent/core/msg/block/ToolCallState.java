package com.reactagent.core.msg.block;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @ClassName: ToolCallState
 * @Description: todo
 * @Author XuZheng
 * @Date 2026/7/22 11:09
 */
@AllArgsConstructor
@Getter
public enum ToolCallState {
    PENDING, ASKING, ALLOWED, SUBMITTED, FINISHED
}
