package com.reactagent.core.msg.block;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

/**
 * @ClassName: TextBlock
 * @Description: todo
 * @Author XuZheng
 * @Date 2026/7/22 11:06
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextBlock implements ContentBlock {
    private String text;
}
