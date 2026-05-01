const { test, expect } = require('@playwright/test');

const BASE = 'http://localhost:4173';

test.describe('/settings — 系统设置', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE}/settings`);
  });

  test('页面标题正确', async ({ page }) => {
    await expect(page).toHaveTitle(/Agent Desk/);
  });

  test('时钟渲染并显示时间', async ({ page }) => {
    const clock = page.locator('#clock-02');
    await expect(clock).toBeVisible();
    await expect(clock).toContainText(/^\d{2}:\d{2}$/);
  });

  test('导航链接存在且正确', async ({ page }) => {
    await expect(page.locator('a[href="/"]')).toBeVisible();
    await expect(page.locator('a[href="/settings"]')).toBeVisible();
    await expect(page.locator('a[href*="robot-settings"]')).toBeVisible();
    // /settings 有两个指向 /integration 的链接（nav 和 platform entry）
    await expect(page.locator('a[href="/integration"]').first()).toBeVisible();
  });

  test('无选中线程时不会给机器人设置链接硬塞默认 thread', async ({ page }) => {
    await expect(page.locator('.top-actions a[href="/robot-settings"]')).toBeVisible();
  });

  test('设置目录包含 4 个分组链接', async ({ page }) => {
    const links = page.locator('.settings-directory a');
    await expect(links).toHaveCount(4);
  });

  test('点击目录链接滚动到对应区域', async ({ page }) => {
    const link = page.locator('.settings-directory a[href="#execution-guard"]');
    await link.click();
    const section = page.locator('#execution-guard');
    await expect(section).toBeVisible();
    await expect(page).toHaveURL(/#execution-guard/);
  });

  test('目录链接点击后添加 is-active', async ({ page }) => {
    const link = page.locator('.settings-directory a[href="#execution-guard"]');
    await link.click();
    await expect(link).toHaveClass(/is-active/);
  });

  test('分段控件切换活跃状态 — 布局密度', async ({ page }) => {
    const segGroup = page.locator('#workspace-defaults .segmented');
    const chips = segGroup.locator('.seg-chip');
    await expect(chips.first()).toHaveClass(/is-active/);

    await chips.last().click();
    await expect(chips.last()).toHaveClass(/is-active/);
    await expect(chips.first()).not.toHaveClass(/is-active/);
  });

  test('分段控件切换活跃状态 — 执行边界', async ({ page }) => {
    const segGroup = page.locator('#execution-guard .segmented');
    const chips = segGroup.locator('.seg-chip');
    await expect(chips.first()).toHaveClass(/is-active/);

    await chips.last().click();
    await expect(chips.last()).toHaveClass(/is-active/);
  });

  test('点击 "立即压缩" 按钮打开压缩模态框', async ({ page }) => {
    const button = page.locator('button[data-modal="compression-settings"]');
    await button.click();

    const modal = page.locator('#modal-host');
    await expect(modal).not.toHaveClass(/is-hidden/);
    await expect(page.locator('#modal-title')).toHaveText('上下文压缩');
  });

  test('压缩模态框 — 取消关闭弹窗', async ({ page }) => {
    await page.locator('button[data-modal="compression-settings"]').click();
    await page.locator('#modal-cancel-button').click();
    await expect(page.locator('#modal-host')).toHaveClass(/is-hidden/);
  });

  test('压缩模态框 — 点击遮罩关闭', async ({ page }) => {
    await page.locator('button[data-modal="compression-settings"]').click();
    // modal-sheet 居中覆盖了 backdrop 中心区域，需要点击边缘
    const backdrop = page.locator('.modal-backdrop');
    await backdrop.click({ position: { x: 5, y: 5 } });
    await expect(page.locator('#modal-host')).toHaveClass(/is-hidden/);
  });

  test('压缩模态框 — Escape 关闭', async ({ page }) => {
    await page.locator('button[data-modal="compression-settings"]').click();
    await page.keyboard.press('Escape');
    await expect(page.locator('#modal-host')).toHaveClass(/is-hidden/);
  });

  test('压缩模态框 — 保存触发 toast', async ({ page }) => {
    await page.locator('button[data-modal="compression-settings"]').click();
    await page.locator('#modal-save-button').click();

    const toast = page.locator('#toast');
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('已触发上下文压缩');
  });

  test('点击 "生成备份" 按钮打开备份模态框', async ({ page }) => {
    await page.locator('button[data-modal="backup-settings"]').click();

    const modal = page.locator('#modal-host');
    await expect(modal).not.toHaveClass(/is-hidden/);
    await expect(page.locator('#modal-title')).toHaveText('上下文内容备份');
  });

  test('备份模态框 — 保存触发 toast', async ({ page }) => {
    await page.locator('button[data-modal="backup-settings"]').click();
    await page.locator('#modal-save-button').click();

    const toast = page.locator('#toast');
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('备份快照已生成');
  });

  test('平台入口 — 任务队列卡片可点击并弹出模态框', async ({ page }) => {
    await page.locator('#open-task-queue').click();
    await expect(page.locator('#modal-title')).toHaveText('任务队列');
  });

  test('平台入口 — 运行日志卡片可点击', async ({ page }) => {
    await page.locator('#open-run-log').click();
    await expect(page.locator('#modal-title')).toHaveText('运行日志');
  });

  test('平台入口 — 工具授权卡片可点击', async ({ page }) => {
    await page.locator('#open-tool-permission').click();
    await expect(page.locator('#modal-title')).toHaveText('工具授权');
  });

  test('平台入口 — 记忆与备份卡片可点击', async ({ page }) => {
    await page.locator('#open-memory-backup').click();
    await expect(page.locator('#modal-title')).toHaveText('记忆与备份');
  });

  test('Skill 与 MCP 管理链接指向正确页面', async ({ page }) => {
    const link = page.locator('a[href="/integration"]').first();
    await expect(link).toBeVisible();
  });

  test('分段控件点击触发 toast 通知', async ({ page }) => {
    const chip = page.locator('#workspace-defaults .seg-chip').last();
    await chip.click();
    const toast = page.locator('#toast');
    await expect(toast).toBeVisible();
  });
});
