/** 4.1.0-fix2 修法：ProLinePage 全部隐式 sync handler → getOrCreateSyncHandler 编号范式。 */
import fs from 'node:fs';

const f = 'src/main/java/com/wztwzt/ae2_qof/proline/gui/ProLinePage.java';
let s = fs.readFileSync(f, 'utf8');

// ① clickServer 定义替换为 serverClick（getOrCreateSyncHandler 编号范式）
s = s.replace(
  /private static InteractionSyncHandler clickServer\(final PanelSyncManager syncManager,\s*java\.util\.function\.Consumer<PanelSyncManager> action\) \{\s*return new InteractionSyncHandler\(\)\.setOnMousePressed\(mouse -> action\.accept\(syncManager\)\);\s*\}/,
`/** fix2：所有交互 handler 必须 getOrCreateSyncHandler 编号（"Widgets created by DynamicSyncHandler…"规则）。 */
    private static InteractionSyncHandler serverClick(final PanelSyncManager syncManager, String key,
        java.util.function.Consumer<PanelSyncManager> action) {
        return syncManager.getOrCreateSyncHandler(
            key,
            InteractionSyncHandler.class,
            () -> new InteractionSyncHandler().setOnMousePressed(mouse -> action.accept(syncManager)));
    }`
);

// ② 主页 bar 三按钮
s = s.replace(
  '.syncHandler(clickServer(syncManager, pm -> {\n                    TileEntity base = terminalBase(terminal);',
  '.syncHandler(clickServer(syncManager, "proline.new", pm -> {\n                    TileEntity base = terminalBase(terminal);'
);
s = s.replace(
  '.syncHandler(clickServer(syncManager, pm -> importPanelHandle.openPanel()))',
  '.syncHandler(clickServer(syncManager, "proline.import.open", pm -> importPanelHandle.openPanel()))'
);
s = s.replace(
  '.syncHandler(clickServer(syncManager, pm -> presetsPanelHandle.openPanel())))',
  '.syncHandler(clickServer(syncManager, "proline.presets.open", pm -> presetsPanelHandle.openPanel())))'
);

// ③ lineRowWidget：选择行 + 设置齿轮
s = s.replace(
  '.syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {\n                    // 服务端：选中该线 + 计入"活跃产线"（采样对象）\n                    SELECTED_LINE.put(',
  '.syncHandler(clickServer(syncManager, "proline.line.select_" + row.index, pm -> {\n                    // 服务端：选中该线 + 计入"活跃产线"（采样对象）\n                    SELECTED_LINE.put('
);
s = s.replace(
  '.syncHandler(clickServer(syncManager, pm -> linePanelHandle.openPanel()));',
  '.syncHandler(clickServer(syncManager, "proline.line.cfg_" + row.index, pm -> linePanelHandle.openPanel()));'
);

// ④ stepRowWidget：名称（开小面板）、数值（开候选）、删除
s = s.replace(
  '.syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {\n                    LAST_STEP.put(\n                        syncManager.getPlayer()', 
  '.syncHandler(clickServer(syncManager, "proline.step.open_" + row.seq, pm -> {\n                    LAST_STEP.put(\n                        pm.getPlayer()'
);
s = s.replace(
  '.syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {\n                    LAST_STEP.put(\n                        syncManager.getPlayer()', 
  '.syncHandler(clickServer(syncManager, "proline.step.bind_" + row.seq, pm -> {\n                    LAST_STEP.put(\n                        pm.getPlayer()'
);
s = s.replace(/\.syncHandler\(\s*clickServer\(\s*syncManager,\s*pm -> \{ ProLineManager\.removeStep\(pm\.getPlayer\(\), row\.lineId, row\.stepId\); \}\)\)\);/g,
  '.syncHandler(clickServer(syncManager, "proline.step.del_" + row.seq, pm -> {\n                    ProLineManager.removeStep(pm.getPlayer(), row.lineId, row.stepId);\n                })));'
);

// ⑤ buildStepPanel：暂停/写卡/关闭
s = s.replace(
  '.syncHandler(clickServer(syncManager, pm -> pausedSync.setValue(!pausedSync.getValue()))));',
  '.syncHandler(clickServer(syncManager, "proline.stepui.pause", pm -> pausedSync.setValue(!pausedSync.getValue()))));'
);
s = s.replace(
  '.syncHandler(clickServer(syncManager, pm -> {\n                    ProductionLine l = lineOf(pm.getPlayer());\n                    ProductionStep s = stepOf(pm.getPlayer());\n                    if (l != null && s != null) {\n                        ItemStack hand = pm.getPlayer().getHeldItem();\n                        ItemProductionLineCard.programCard(hand, l.id, s.id);\n                    }\n                })));',
  '.syncHandler(clickServer(syncManager, "proline.stepui.card", pm -> {\n                    ProductionLine l = lineOf(pm.getPlayer());\n                    ProductionStep s = stepOf(pm.getPlayer());\n                    if (l != null && s != null) {\n                        ItemStack hand = pm.getPlayer().getHeldItem();\n                        ItemProductionLineCard.programCard(hand, l.id, s.id);\n                    }\n                })));'
);

// ⑥ buildLinePanel：整线暂停 / 删除
s = s.replace(
  '.syncHandler(clickServer(syncManager, pm -> pauseSync.setValue(!pauseSync.getValue()))));',
  '.syncHandler(clickServer(syncManager, "proline.lineui.pause", pm -> pauseSync.setValue(!pauseSync.getValue()))));'
);
s = s.replace(
  '.syncHandler(clickServer(syncManager, pm -> {\n                    ProductionLine l = lineOf(pm.getPlayer());\n                    if (l == null) return;',
  '.syncHandler(clickServer(syncManager, "proline.lineui.delete", pm -> {\n                    ProductionLine l = lineOf(pm.getPlayer());\n                    if (l == null) return;'
);

// ⑦ 关闭按钮 ×5 → getOrCreate（按 panel key）
s = s.replace(/InteractionSyncHandler closeBtn = new InteractionSyncHandler\(\)\s*\.setOnMousePressed\(mouse -> panelHandler\.closePanel\(\)\);/g,
  'InteractionSyncHandler closeBtn = syncManager.getOrCreateSyncHandler(\n            "proline.close",\n            InteractionSyncHandler.class,\n            () -> new InteractionSyncHandler().setOnMousePressed(mouse -> panelHandler.closePanel()));');

// ⑧ 导入面板：解析并创建
s = s.replace(
  '.syncHandler(clickServer(syncManager, pm -> runImport(pm, terminal)));',
  '.syncHandler(clickServer(syncManager, "proline.import.run", pm -> runImport(pm, terminal)));'
);

// ⑨ 候选行单行绑定/解绑（行内 handler）
s = s.replace(
  '.syncHandler(clickServer(syncManager, sm -> {',
  '.syncHandler(clickServer(syncManager, "proline.cand." + row.dim + "_" + row.x + "_" + row.y + "_" + row.z, sm -> {'
);

// ⑩ 预置行单行创建
s = s.replace(
  '.syncHandler(clickServer(syncManager, sm -> {\n                        TileEntity base = terminalBase(terminal);',
  '.syncHandler(clickServer(syncManager, "proline.preset.row_" + row.index, sm -> {\n                        TileEntity base = terminalBase(terminal);'
);

fs.writeFileSync(f, s);
const left = (s.match(/clickServer\(/g) || []).length - (s.match(/clickServer\(/g) || []).filter(m => false).length;
console.log('patched. clickServer 调用剩余（应全部带 key）：', (s.match(/clickServer\(\s*syncManager,\s*\"/g) || []).length, '处带键；无键残留:',
  (s.match(/clickServer\(syncManager, (pm|sm) ->/g) || []).length);
console.log('implicit new InteractionSyncHandler 挂载残留:', (s.match(/\.syncHandler\(new InteractionSyncHandler\(\)/g) || []).length);
