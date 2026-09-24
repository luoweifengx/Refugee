package luowei.refugee.network;

import java.util.UUID;

/**
 * 人员关系玩家列表的一行：玩家、显示名、副标题。
 */
public record RelationsPlayerRow(UUID id, String name, String detail) {
}
