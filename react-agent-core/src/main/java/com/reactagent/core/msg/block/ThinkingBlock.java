package com.reactagent.core.msg.block;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

/**
 * @ClassName: ThinkingBlock
 * @Description: todo
 * @Author XuZheng
 * @Date 2026/7/22 11:07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThinkingBlock implements ContentBlock {
    private String thinking;
}
