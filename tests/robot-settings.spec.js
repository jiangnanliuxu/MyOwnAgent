const { test, expect } = require('@playwright/test');

const BASE = 'http://localhost:4173';

test.describe('/robot-settings — 机器人设置', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE}/robot-settings?thread=session-review`);
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

  test('当前页导航链接保持静态 URL', async ({ page }) => {
    await expect(page.locator('.top-actions a.nav-pill.is-active')).toHaveAttribute('href', '/robot-settings');
  });

  test('URL query param 加载正确会话上下文', async ({ page }) => {
    await expect(page.locator('#selected-thread-label')).toHaveText('review-agent');
    await expect(page.locator('#selected-thread-file')).toHaveText('src/auth');
  });

  test('角色数量显示正确', async ({ page }) => {
    await expect(page.locator('#role-count')).toHaveText('2 个');
    await expect(page.locator('#context-role-total')).toHaveText('2 个角色');
  });

  test('角色目录渲染正确角色', async ({ page }) => {
    const items = page.locator('#agent-directory .agent-item');
    await expect(items).toHaveCount(2);
  });

  test('角色目录中活跃角色高亮', async ({ page }) => {
    // focusRole 是 review
    const activeItem = page.locator('#agent-directory .agent-item.is-active');
    await expect(activeItem).toContainText('review-agent');
  });

  test('点击角色目录项切换活跃角色', async ({ page }) => {
    await page.locator('#agent-directory .agent-item').first().click();

    // 点击同目录下另一个会话角色后，角色详情应该更新
    await page.locator('#agent-directory .agent-item').nth(1).click();
    await expect(page.locator('#active-role-name')).toHaveText('auth-agent');
  });

  test('活跃角色信息正确渲染', async ({ page }) => {
    await expect(page.locator('#active-role-name')).toHaveText('review-agent');
    await expect(page.locator('#active-role-tag')).toHaveText('Session Role');
    await expect(page.locator('#active-role-model')).toHaveText('GPT-5.4-mini');
  });

  test('角色矩阵渲染', async ({ page }) => {
    const cards = page.locator('.role-matrix-card');
    await expect(cards).toHaveCount(2);
  });

  test('角色矩阵卡片点击切换活跃角色', async ({ page }) => {
    const firstMatrixCard = page.locator('.role-matrix-card').first();
    await firstMatrixCard.click();
    await expect(firstMatrixCard).toHaveClass(/is-active/);
    await expect(page.locator('#active-role-name')).toHaveText('review-agent');
  });

  test('切换同目录会话角色后详情更新', async ({ page }) => {
    await expect(page.locator('#active-role-name')).toHaveText('review-agent');

    await page.locator('#agent-directory .agent-item').nth(1).click();
    await expect(page.locator('#active-role-name')).toHaveText('auth-agent');
    await expect(page.locator('#active-role-compression')).toHaveText('轻压缩');
  });

  test('切换模型按钮打开模态框', async ({ page }) => {
    await page.locator('#open-model-config').click();
    await expect(page.locator('#modal-host')).not.toHaveClass(/is-hidden/);
    await expect(page.locator('#modal-title')).toContainText('模型接入');
  });

  test('模型配置模态框 — 表单字段预填充', async ({ page }) => {
    await page.locator('#open-model-config').click();

    // 检查供应商名和模型名被预填充
    await expect(page.locator('#provider-name-input')).toHaveValue('OpenAI');
    await expect(page.locator('#provider-model-input')).toHaveValue('GPT-5.4-mini');
  });

  test('模型配置模态框 — 保存更新并触发 toast', async ({ page }) => {
    await page.locator('#open-model-config').click();

    // 修改供应商名
    await page.locator('#provider-name-input').fill('Custom Provider');
    await page.locator('#modal-save-button').click();

    const toast = page.locator('#toast');
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('模型配置已更新');
  });

  test('调整强度按钮打开压缩配置模态框', async ({ page }) => {
    await page.locator('#open-compression-config').click();
    await expect(page.locator('#modal-title')).toContainText('压缩强度');
  });

  test('压缩配置模态框 — 保存触发 toast', async ({ page }) => {
    await page.locator('#open-compression-config').click();
    await page.locator('#modal-save-button').click();
    await expect(page.locator('#toast')).toContainText('压缩强度已保存');
  });

  test('修改角色说明按钮打开模态框', async ({ page }) => {
    await page.locator('#edit-role-description').click();
    await expect(page.locator('#modal-title')).toContainText('角色说明');
  });

  test('角色说明模态框 — 表单预填充', async ({ page }) => {
    await page.locator('#edit-role-description').click();
    await expect(page.locator('#role-name-input')).toHaveValue('review-agent');
    await expect(page.locator('#role-alias-input')).toHaveValue('review-agent-session');
  });

  test('角色说明模态框 — 保存更新并触发 toast', async ({ page }) => {
    await page.locator('#edit-role-description').click();
    await page.locator('#modal-save-button').click();
    await expect(page.locator('#toast')).toContainText('角色说明已更新');
  });

  test('编辑提示前缀按钮打开模态框', async ({ page }) => {
    await page.locator('#edit-prompt-prefix').click();
    await expect(page.locator('#modal-title')).toContainText('提示前缀');
  });

  test('提示前缀模态框 — 保存触发 toast', async ({ page }) => {
    await page.locator('#edit-prompt-prefix').click();
    await page.locator('#modal-save-button').click();
    await expect(page.locator('#toast')).toContainText('提示前缀已更新');
  });

  test('复制当前角色配置按钮打开快照模态框', async ({ page }) => {
    await page.locator('#copy-role-config').click();
    await expect(page.locator('#modal-title')).toContainText('当前配置');
  });

  test('别名输入框存在且可编辑', async ({ page }) => {
    const input = page.locator('#alias-input');
    await expect(input).toHaveValue('review-agent-session');

    await input.fill('custom-alias');
    await expect(input).toHaveValue('custom-alias');
  });

  test('自定义职责输入框存在', async ({ page }) => {
    const input = page.locator('#custom-duty-input');
    await expect(input).toBeVisible();
  });

  test('职责复选框渲染', async ({ page }) => {
    const checkboxes = page.locator('#role-duty-options input[type="checkbox"]');
    await expect(checkboxes).toHaveCount(4);
  });

  test('快速操作按钮也触发对应模态框', async ({ page }) => {
    // agent-identity 区域内的按钮也应该能工作
    await page.locator('.quick-edit-button').first().click();
    await expect(page.locator('#modal-host')).not.toHaveClass(/is-hidden/);
  });

  test('带不同 thread 参数加载不同上下文', async ({ page }) => {
    await page.goto(`${BASE}/robot-settings?thread=route-test`);
    await expect(page.locator('#selected-thread-label')).toHaveText('test-agent');
    await expect(page.locator('#selected-thread-file')).toHaveText('src/router');
  });

  test('无 thread 参数时回退到默认会话', async ({ page }) => {
    await page.goto(`${BASE}/robot-settings`);
    await expect(page.locator('#selected-thread-label')).toHaveText('review-agent');
  });
});
