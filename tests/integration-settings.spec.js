const { test, expect } = require('@playwright/test');

const BASE = 'http://localhost:4173';

test.describe('/integration — 能力管理', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE}/integration`);
  });

  test('页面标题正确', async ({ page }) => {
    await expect(page).toHaveTitle(/Agent Desk/);
  });

  test('时钟渲染', async ({ page }) => {
    const clock = page.locator('#clock-02');
    await expect(clock).toBeVisible();
  });

  test('导航链接存在', async ({ page }) => {
    await expect(page.locator('a[href="/"]')).toBeVisible();
    await expect(page.locator('a[href="/settings"]')).toBeVisible();
    await expect(page.locator('a[href*="robot-settings"]')).toBeVisible();
    await expect(page.locator('a[href="/integration"]')).toBeVisible();
  });

  test('无选中线程时不会给机器人设置链接硬塞默认 thread', async ({ page }) => {
    await expect(page.locator('.top-actions a[href="/robot-settings"]')).toBeVisible();
  });

  test('管理目录包含 4 个链接', async ({ page }) => {
    const links = page.locator('.settings-directory a');
    await expect(links).toHaveCount(4);
  });

  test('目录链接点击滚动到对应区域', async ({ page }) => {
    await page.locator('.settings-directory a[href="#mcp-overview"]').click();
    await expect(page).toHaveURL(/#mcp-overview/);
  });

  test('Skill 目录渲染卡片', async ({ page }) => {
    const cards = page.locator('#skill-catalog .catalog-card');
    await expect(cards).toHaveCount(3);
  });

  test('Skill 数量标签更新', async ({ page }) => {
    await expect(page.locator('#skill-count-label')).toHaveText('3 个已装载');
  });

  test('Skill 卡片包含名称和状态', async ({ page }) => {
    const firstCard = page.locator('#skill-catalog .catalog-card').first();
    await expect(firstCard).toContainText('frontend-design');
    await expect(firstCard).toContainText('启用');
  });

  test('Skill 挂载网格渲染', async ({ page }) => {
    const mounts = page.locator('#skill-mount-grid .mount-card');
    await expect(mounts).toHaveCount(3);
  });

  test('Skill 启用/停用切换', async ({ page }) => {
    const toggleButton = page.locator('[data-skill-toggle="frontend-design"]');
    await toggleButton.click();

    // 状态应变更为停用
    const firstCard = page.locator('#skill-catalog .catalog-card').first();
    await expect(firstCard).toContainText('停用');
  });

  test('Skill 配置按钮打开模态框', async ({ page }) => {
    await page.locator('[data-skill-config="frontend-design"]').click();
    await expect(page.locator('#modal-host')).not.toHaveClass(/is-hidden/);
    await expect(page.locator('#modal-title')).toContainText('Skill 配置');
  });

  test('Skill 配置模态框 — 保存触发 toast', async ({ page }) => {
    await page.locator('[data-skill-config="frontend-design"]').click();
    await page.locator('#modal-save-button').click();
    await expect(page.locator('#toast')).toContainText('Skill 配置已保存');
  });

  test('新增 Skill 按钮打开空白模态框', async ({ page }) => {
    await page.locator('#open-install-skill').click();
    await expect(page.locator('#modal-title')).toHaveText('新增 Skill');
  });

  test('新增 Skill — 保存后新增卡片', async ({ page }) => {
    await page.locator('#open-install-skill').click();
    await page.locator('#skill-name-input').fill('test-skill');
    await page.locator('#skill-scope-input').fill('测试范围');
    await page.locator('#modal-save-button').click();

    // 卡片数量应变为 4
    const cards = page.locator('#skill-catalog .catalog-card');
    await expect(cards).toHaveCount(4);
    await expect(cards.first()).toContainText('test-skill');
  });

  test('同步装载策略按钮触发 toast', async ({ page }) => {
    await page.locator('#open-sync-skill-policy').click();
    await expect(page.locator('#toast')).toContainText('装载策略已同步');
  });

  test('MCP 端点列表渲染', async ({ page }) => {
    const endpoints = page.locator('#mcp-endpoint-list .endpoint-card');
    await expect(endpoints).toHaveCount(3);
  });

  test('MCP 端点数量标签更新', async ({ page }) => {
    await expect(page.locator('#endpoint-count-label')).toHaveText('3 条接口');
  });

  test('MCP 端点检查按钮更新状态', async ({ page }) => {
    await page.locator('[data-endpoint-check="figma"]').click();
    const toast = page.locator('#toast');
    await expect(toast).toContainText('Figma MCP 检查完成');
  });

  test('MCP 端点编辑按钮打开模态框', async ({ page }) => {
    await page.locator('[data-endpoint-edit="figma"]').click();
    await expect(page.locator('#modal-host')).not.toHaveClass(/is-hidden/);
    await expect(page.locator('#modal-title')).toContainText('MCP 接口');
  });

  test('MCP 端点编辑 — 保存触发 toast', async ({ page }) => {
    await page.locator('[data-endpoint-edit="figma"]').click();
    await page.locator('#modal-save-button').click();
    await expect(page.locator('#toast')).toContainText('MCP 接口已保存');
  });

  test('新增接口按钮打开空白模态框', async ({ page }) => {
    await page.locator('#open-add-endpoint').click();
    await expect(page.locator('#modal-title')).toHaveText('新增 MCP 接口');
  });

  test('批量健康检查按钮更新所有端点', async ({ page }) => {
    await page.locator('#open-endpoint-check').click();
    await expect(page.locator('#toast')).toContainText('批量健康检查完成');
  });

  test('运行健康列表渲染', async ({ page }) => {
    const items = page.locator('#integration-health-list .health-item');
    await expect(items).toHaveCount(4);
  });

  test('最近变更标签显示', async ({ page }) => {
    await expect(page.locator('#latest-skill-change')).toContainText('frontend-design');
  });
});
