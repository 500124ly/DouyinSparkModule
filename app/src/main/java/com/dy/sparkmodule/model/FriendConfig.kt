package com.dy.sparkmodule.model

import org.json.JSONObject

/**
 * 单个目标好友配置。
 * @param name 好友昵称（主键，状态记录、列表展示均以此为准）
 * @param note 备注名（可选）；与昵称任一在节点文本中命中即视为匹配
 * @param customText 该好友专属文案；为 null/空 时使用全局轮换文案
 */
data class FriendConfig(
    val name: String,
    val note: String? = null,
    val customText: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("note", note ?: "")
        put("customText", customText ?: "")
    }

    /** 判断节点文本是否命中该好友：昵称或备注任一包含即可 */
    fun matches(text: String): Boolean {
        if (name.isNotBlank() && text.contains(name)) return true
        if (!note.isNullOrBlank() && text.contains(note)) return true
        return false
    }

    companion object {
        fun fromJson(o: JSONObject): FriendConfig = FriendConfig(
            name = o.optString("name").trim(),
            note = o.optString("note").ifBlank { null },
            customText = o.optString("customText").ifBlank { null }
        )
    }
}
