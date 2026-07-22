package com.reactagent.core.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 元数据(L0),始终注入 system prompt。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMeta {
    private String name;
    private String description;
    private String whenToUse;

    @Override
    public String toString() {
        return "- " + name + ": " + description + " (使用场景: " + whenToUse + ")";
    }
}
