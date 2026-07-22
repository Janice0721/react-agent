package com.reactagent.core.msg.block;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

/**
 * @ClassName: ToolResultBlock
 * @Description: todo
 * @Author XuZheng
 * @Date 2026/7/22 11:09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResultBlock implements ContentBlock {
   private String id;
   private String name;
   private String output;
   private ToolResultState state;
}
