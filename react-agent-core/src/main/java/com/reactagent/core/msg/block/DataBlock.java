package com.reactagent.core.msg.block;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

/**
 * @ClassName: DataBlock
 * @Description: todo
 * @Author XuZheng
 * @Date 2026/7/22 11:10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataBlock implements ContentBlock {
    private String mediaType;
    private String data;
}
