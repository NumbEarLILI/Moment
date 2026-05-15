package com.example.moment.domain.usecase

enum class DiaryGenerationMode {
    /**
     * 若设置里已配置 API 地址与模型名，则调用大模型；否则使用内置规则。
     * 大模型调用失败时抛出异常（由界面展示错误）。
     */
    AUTO,

    /** 仅使用规则生成（单元测试等）。 */
    RULE_BASED_ONLY
}
