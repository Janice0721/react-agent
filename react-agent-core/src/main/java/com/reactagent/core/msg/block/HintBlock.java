package com.reactagent.core.msg.block;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

/**
 * @ClassName: HintBlock
 * @Description: todo
 * @Author XuZheng
 * @Date 2026/7/22 11:11
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HintBlock implements ContentBlock {
    private String hint;
    private String source;
}
