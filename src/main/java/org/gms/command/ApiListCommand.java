package org.gms.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "apis", description = "列出服务端所有 API，可带关键词过滤")
public class ApiListCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
    boolean help;

    @Parameters(index = "0", arity = "0..1", description = "可选的关键词过滤，如 auth、server、drop")
    String keyword;

    // 敏感接口正则：需要 --force 才能执行
    private static final String[] SENSITIVE = {
            ".*/shutdown.*",
            ".*/stopServer.*",
            ".*/restartServer.*",
            ".*/resetRate.*",
            ".*/resetRates.*",
            ".*/ban.*",
            ".*/unban.*",
            ".*/delete.*",
            "DELETE .*",
            ".*/update.*",
            ".*/add.*",
            ".*/onSale.*",
            ".*/offSale.*",
            ".*/batchOnSale.*",
            ".*/importYml.*",
            ".*/tree/write.*",
            ".*/give/resource.*",
            ".*/reload.*",
            ".*/reset/logged.*",
    };

    record ApiEntry(String method, String path, String summary, boolean sensitive) {}

    private static final List<ApiEntry> APIS = List.of(
            // === Auth ===
            e("POST", "/auth/v1/login",          "登录，body: {\"username\":\"...\", \"password\":\"...\"}", false),
            e("DELETE", "/auth/v1/logout",        "退出登录", true),
            e("GET",    "/auth/v1/refreshToken",  "刷新 token (Header: Authorization)", false),

            // === Account ===
            e("GET",  "/account/v1/info",                   "获取当前用户信息", false),
            e("GET",  "/account/v1",                         "账号列表 ?page=&size=&id=&name=&lastLoginStart=&lastLoginEnd=&createdAtStart=&createdAtEnd=", false),
            e("POST", "/account/v1",                         "注册账号，body: {\"name\":\"...\", \"password\":\"...\", \"language\":2} (2=English, 3=中文)", true),
            e("PUT",  "/account/v1",                         "修改当前用户信息 (需旧密码)", true),
            e("PUT",  "/account/v1/{id}",                   "GM 修改指定账号", true),
            e("DELETE", "/account/v1/{id}",                  "删除账号", true),
            e("PUT",  "/account/v1/{id}/reset/logged",      "重置在线状态", true),
            e("PUT",  "/account/v1/{id}/ban",               "封禁账号，body: {\"reason\":\"...\"}", true),
            e("PUT",  "/account/v1/{id}/unban",             "解封账号", true),

            // === Server ===
            e("GET",  "/server/v1/online",     "服务器在线状态", false),
            e("GET",  "/server/v1/version",    "服务器版本号", false),
            e("GET",  "/server/v1/shutdown",    "关闭整个 JVM 进程", true),
            e("GET",  "/server/v1/stopServer",  "停止游戏服务器", true),
            e("POST", "/server/v1/stopServerWithMsgAndInternal", "自定义关服通知+倒计时(分钟)，body: {\"message\":\"...\", \"internal\":5}", true),
            e("GET",  "/server/v1/startServer",  "启动服务器", true),
            e("GET",  "/server/v1/restartServer","重启服务器", true),
            e("GET",  "/server/v1/world/list",      "世界列表", false),
            e("GET",  "/server/v1/channel/list",    "频道列表 ?worldId=<id>", false),

            // === Character ===
            e("POST", "/character/v1/updateRate",        "设置玩家个人倍率 (expRate/mesoRate/dropRate)，body: {\"extendName\":\"expRate\", \"characterId\":..., \"value\":...}", true),
            e("POST", "/character/v1/resetRate",         "重置单个倍率", true),
            e("GET",  "/character/v1/resetRates",        "重置全部倍率", true),
            e("POST", "/character/v1/online/list",       "在线角色列表 (分页)，body: {\"pageNo\":1, \"pageSize\":10, \"name\":\"...\"}", false),

            // === Common ===
            e("POST", "/common/v1/getEquipmentInfoByItemId",       "根据道具 ID 查装备信息，body: {\"id\":...}", false),
            e("POST", "/common/v1/getAllWorldsOnlinePlayersCount", "查询各世界在线人数，body: {\"worldIdList\":[...]}", false),
            e("POST", "/common/v1/informationSearch",              "资料查询，body: {\"types\":[\"mob\",\"eqp\",\"etc\",...], \"filter\":\"...\"} (types: cash/consume/eqp/etc/ins/map/mob/npc/pet/skill)", false),

            // === CashShop ===
            e("GET",  "/cashShop/v1/getAllCategoryList",   "商城分类列表", false),
            e("POST", "/cashShop/v1/getCommodityByCategory","按分类查商品 (分页)，body: {\"pageNo\":1, \"pageSize\":10, \"subId\":..., \"onSale\":true}", false),
            e("GET",  "/cashShop/v1/getCommodityBySn/{sn}","按 SN 查商品详情", false),
            e("POST", "/cashShop/v1/onSale",               "上架商品，body: {\"sn\":...}", true),
            e("POST", "/cashShop/v1/offSale",              "下架商品，body: {\"sn\":...}", true),
            e("POST", "/cashShop/v1/batchOnSale",          "批量上架，body: {\"data\":[...], \"type\":\"...\", \"value\":...}", true),

            // === Drop ===
            e("POST",   "/drop/v1/getDropList",           "怪物掉落列表 (分页)，body: {\"pageNo\":1, \"pageSize\":10, \"dropperName\":\"蓝蜗牛\", \"itemName\":\"...\"}", false),
            e("POST",   "/drop/v1/getGlobalDropList",     "全局掉落列表 (分页)，body: {\"pageNo\":1, \"pageSize\":10, \"itemName\":\"...\"}", false),
            e("PUT",    "/drop/v1/addDropData",           "添加怪物掉落，body: {\"dropperId\":..., \"itemId\":..., \"chance\":..., ...}", true),
            e("PUT",    "/drop/v1/addGlobalDropData",     "添加全局掉落，body: {\"itemId\":..., \"chance\":..., ...}", true),
            e("POST",   "/drop/v1/updateDropData",        "更新怪物掉落，body: {\"id\":..., ...}", true),
            e("POST",   "/drop/v1/updateGlobalDropData",  "更新全局掉落，body: {\"id\":..., ...}", true),
            e("DELETE", "/drop/v1/deleteDropData/{id}",   "删除怪物掉落", true),
            e("DELETE", "/drop/v1/deleteGlobalDropData/{id}","删除全局掉落", true),

            // === Gachapon ===
            e("POST", "/gachapon/v1/getPools",     "扭蛋奖池列表，body: {\"pageNo\":1, \"pageSize\":10, \"gachaponId\":...}", false),
            e("POST", "/gachapon/v1/updatePool",   "创建/更新奖池，body: {\"id\":..., \"name\":\"...\", ...}", true),
            e("POST", "/gachapon/v1/deletePool",   "删除奖池，body: {\"id\":...}", true),
            e("POST", "/gachapon/v1/getRewards",   "查询奖池奖励，body: {\"id\":<poolId>}", false),
            e("POST", "/gachapon/v1/updateReward", "创建/更新奖励，body: {\"poolId\":..., \"itemId\":..., ...}", true),
            e("POST", "/gachapon/v1/deleteReward", "删除奖励，body: {\"id\":...}", true),

            // === Give ===
            e("POST", "/give/v1/resource", "发放资源给玩家，body: {\"worldId\":..., \"player\":\"...\", \"type\":0, \"id\":..., \"quantity\":...}", true),

            // === Inventory ===
            e("GET",  "/inventory/v1/getInventoryTypeList", "背包分类列表", false),
            e("POST", "/inventory/v1/getCharacterList",     "按条件搜索角色 (分页)，body: {\"pageNo\":1, \"pageSize\":10, \"characterName\":\"...\"}", false),
            e("POST", "/inventory/v1/getInventoryList",   "查角色背包道具 (分页)，body: {\"pageNo\":1, \"pageSize\":10, \"characterId\":..., \"inventoryType\":...}", false),
            e("POST", "/inventory/v1/updateInventory",    "修改背包道具，body: {\"id\":..., \"quantity\":...}", true),
            e("POST", "/inventory/v1/deleteInventory",    "删除背包道具", true),

            // === Shop ===
            e("POST",   "/shop/v1/getShopList",           "商店列表 (分页)，body: {\"pageNo\":1, \"pageSize\":10, \"shopId\":..., \"npcName\":\"...\"}", false),
            e("POST",   "/shop/v1/getShopItemList",       "商店道具列表 (分页)，body: {\"pageNo\":1, \"pageSize\":10, \"shopId\":...}", false),
            e("GET",    "/shop/v1/getShopItem/{id}",     "按 ID 查道具", false),
            e("PUT",    "/shop/v1/addShopItem",          "添加商店道具，body: {\"shopId\":..., \"itemId\":..., \"price\":..., \"position\":...}", true),
            e("POST",   "/shop/v1/updateShopItem",       "更新商店道具，body: {\"id\":..., ...}", true),
            e("DELETE", "/shop/v1/deleteShopItem/{id}",  "删除商店道具", true),

            // === Config ===
            e("GET",    "/config/v1/getConfigTypeList",  "配置分类列表", false),
            e("POST",   "/config/v1/getConfigList",      "配置列表 (分页)，body: {\"pageNo\":1, \"pageSize\":10, \"type\":\"...\", \"filter\":\"...\"}", false),
            e("POST",   "/config/v1/addConfig",          "添加配置", true),
            e("POST",   "/config/v1/updateConfig",       "更新配置", true),
            e("DELETE", "/config/v1/deleteConfig/{id}",  "删除配置", true),
            e("POST",   "/config/v1/deleteConfigList",   "批量删除配置，body: [1, 2, 3]", true),
            e("POST",   "/config/v1/importYml",          "从 YAML 导入配置 (multipart file)", true),
            e("GET",    "/config/v1/exportYml",          "导出配置为 YAML 文件", false),

            // === Command ===
            e("POST", "/command/v1/getCommandListFromDB",    "GM 命令列表 (分页)，body: {\"pageNo\":1, \"pageSize\":10, \"description\":\"...\", \"enabled\":true}", false),
            e("POST", "/command/v1/updateCommand",          "更新命令状态", true),
            e("GET",  "/command/v1/reloadEventsByGMCommand","重载事件脚本", true),
            e("GET",  "/command/v1/reloadPortalsByGMCommand","重载传送脚本", true),
            e("GET",  "/command/v1/reloadMapsByGMCommand",   "重载地图脚本", true),

            // === File ===
            e("POST", "/file/v1/tree",       "读取文件树，body: {\"currentKey\":\"...\"}", false),
            e("POST", "/file/v1/tree/read",  "读取文件内容，body: {\"currentKey\":\"...\", \"title\":\"...\"}", false),
            e("POST", "/file/v1/tree/write", "写入文件内容，body: {\"currentKey\":\"...\", \"title\":\"...\", \"content\":\"...\"}", true),

            // === Autoban ===
            e("GET",  "/autoban/v1/getConfigList",  "自动封禁配置列表", false),
            e("POST", "/autoban/v1/updateConfig",   "更新自动封禁配置", true)
    );

    private static ApiEntry e(String method, String path, String summary, boolean sensitive) {
        return new ApiEntry(method, path, summary, sensitive);
    }

    public static boolean isSensitive(String method, String path) {
        // 优先精确路径匹配，避免 /account/v1/info 被 /account/v1/{id} 误杀
        for (var a : APIS) {
            if (a.path.equals(path)) {
                return a.sensitive;
            }
        }
        return APIS.stream().anyMatch(a -> pathMatches(a.path, path) && a.sensitive);
    }

    private static boolean pathMatches(String template, String actual) {
        return actual.matches(template.replaceAll("\\{[^}]+}", "[^/]+"));
    }

    @Override
    public Integer call() {
        var filtered = APIS.stream()
                .filter(a -> keyword == null || a.summary.toLowerCase().contains(keyword.toLowerCase())
                        || a.path.toLowerCase().contains(keyword.toLowerCase()))
                .toList();

        System.out.println("BeiDou-Server API 列表 (Server v" + BEIDOU_VERSION + ", build " + BEIDOU_BUILD + ")");
        if (keyword != null) {
            System.out.println("过滤: \"" + keyword + "\"");
        }
        System.out.println("标记 [敏感] 的接口需要用 --force 强制执行\n");

        for (var api : filtered) {
            var flag = api.sensitive ? " [敏感]" : "";
            System.out.printf("  %-7s %-45s %s%s%n", api.method, api.path, api.summary, flag);
        }
        System.out.println("\n共 " + filtered.size() + " 个接口" + (keyword != null ? "（匹配 " + APIS.size() + " 个）" : ""));
        return 0;
    }

    public static final String BEIDOU_VERSION = "1.10";
    public static final String BEIDOU_BUILD = "2025-06-22 12:45:59";
}
