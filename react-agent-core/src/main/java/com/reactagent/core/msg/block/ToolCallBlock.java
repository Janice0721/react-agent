package com.reactagent.core.msg.block;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

/**
 * @ClassName: ToolCallBlock
 * @Description: todo
 * @Author XuZheng
 * @Date 2026/7/22 11:07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallBlock implements ContentBlock {
    private String id;
    private String name;
    private String input;
    private ToolCallState state;
}
