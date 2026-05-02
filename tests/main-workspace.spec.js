const { test, expect } = require('@playwright/test');
const path = require('path');

const BASE = 'http://localhost:4173';

// 辅助：在折叠的 accordion 中点击线程卡片（鼠标事件可能被 accordion summary 拦截）
async function clickThreadCard(page, threadId) {
  const card = page.locator(`.agent-thread-card[data-thread-id="${threadId}"]`);
  // 先展开所属的 accordion
  const accordion = page.locator(`.file-accordion:has(.agent-thread-card[data-thread-id="${threadId}"])`);
  await accordion.evaluate(el => { el.open = true; });
  await card.waitFor({ state: 'visible' });
  await card.click();
}

test.describe('/ — 主工作台', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE}/`);
  });

  test('页面标题正确', async ({ page }) => {
    await expect(page).toHaveTitle(/Agent Desk/);
  });

  test('时钟渲染并显示时间', async ({ page }) => {
    const clock = page.locator('#clock-02');
    await expect(clock).toBeVisible();
    await expect(clock).not.toHaveText('');
    await expect(clock).toContainText(/^\d{2}:\d{2}$/);
  });

  test('初始活跃线程为 session-review', async ({ page }) => {
    const activeCard = page.locator('.agent-thread-card.is-active');
    await expect(activeCard).toHaveAttribute('data-thread-id', 'session-review');
  });

  test('点击线程卡片切换活跃状态', async ({ page }) => {
    await clickThreadCard(page, 'route-primary');
    const routeCard = page.locator('.agent-thread-card[data-thread-id="route-primary"]');
    await expect(routeCard).toHaveClass(/is-active/);
    const sessionCard = page.locator('.agent-thread-card[data-thread-id="session-review"]');
    await expect(sessionCard).not.toHaveClass(/is-active/);
  });

  test('活跃线程切换时更新目录标题', async ({ page }) => {
    const title = page.locator('#active-file-title');
    await expect(title).toHaveText('src/auth');

    await clickThreadCard(page, 'route-primary');
    await expect(title).toHaveText('src/router');
  });

  test('活跃线程切换时更新 composer 线程标签', async ({ page }) => {
    const chip = page.locator('#composer-thread-chip');
    await expect(chip).toContainText('review-agent');

    await clickThreadCard(page, 'route-primary');
    await expect(chip).toContainText('route-agent');
  });

  test('会话窗口显示当前工作 agent 并可切换', async ({ page }) => {
    const currentAgent = page.locator('#current-workflow-agent');
    await expect(currentAgent).toHaveText('Development Agent');
    await expect(page.locator('#workflow-agent-strip')).toContainText('实现项目需求代码');

    await page.locator('.workflow-agent-chip', { hasText: 'Testing Agent' }).click();

    await expect(currentAgent).toHaveText('Testing Agent');
    await expect(page.locator('#workflow-agent-strip')).toContainText('测试验证');
    await expect(page.locator('#composer-context-chip')).toContainText('当前工作：Testing Agent');
  });

  test('后端模式入口默认停留在 Mock 配置状态', async ({ page }) => {
    const control = page.locator('#backend-session-control');
    await expect(control).toContainText('Mock 模式');
    await control.click();
    await expect(page.locator('#modal-title')).toHaveText('后端地址');
    await expect(page.locator('#backend-base-url-input')).toHaveValue('http://localhost:18080');
  });

  test('包含活跃线程的 accordion 自动展开', async ({ page }) => {
    const firstAccordion = page.locator('.file-accordion').first();
    await expect(firstAccordion).toHaveAttribute('open', '');

    await clickThreadCard(page, 'route-primary');
    const secondAccordion = page.locator('.file-accordion').nth(1);
    await expect(secondAccordion).toHaveAttribute('open', '');
  });

  test('点击 accordion summary 选中第一个线程', async ({ page }) => {
    const secondSummary = page.locator('.file-accordion').nth(1).locator('summary');
    await secondSummary.click();
    const title = page.locator('#active-file-title');
    await expect(title).toHaveText('src/router');
  });

  test('点击已展开 accordion summary 可以折叠', async ({ page }) => {
    const firstAccordion = page.locator('.file-accordion').first();
    const firstSummary = firstAccordion.locator('summary');
    await expect(firstAccordion).toHaveAttribute('open', '');

    await firstSummary.click();

    await expect(firstAccordion).not.toHaveAttribute('open', '');
    await expect(page.locator('.agent-thread-card.is-active')).toHaveAttribute('data-thread-id', 'session-review');
  });

  test('键盘操作 summary 与鼠标折叠和展开行为一致', async ({ page }) => {
    const firstAccordion = page.locator('.file-accordion').first();
    const firstSummary = firstAccordion.locator('summary');

    await firstSummary.focus();
    await firstSummary.press('Space');
    await expect(firstAccordion).not.toHaveAttribute('open', '');

    await firstSummary.press('Enter');
    await expect(firstAccordion).toHaveAttribute('open', '');
    await expect(page.locator('.agent-thread-card.is-active')).toHaveAttribute('data-thread-id', 'session-review');
  });

  test('导航链接指向正确页面', async ({ page }) => {
    const settingsLink = page.locator('a[href="/settings"]');
    await expect(settingsLink).toBeVisible();

    // updateRobotLinks() 会将 robot-settings href 动态添加 ?thread= 参数
    const robotLink = page.locator('a[href*="robot-settings"]');
    await expect(robotLink).toBeVisible();
    await expect(robotLink).toHaveAttribute('href', /thread=session-review/);

    const integrationLink = page.locator('a[href="/integration"]');
    await expect(integrationLink).toBeVisible();
  });

  test('机器人设置链接包含当前线程参数', async ({ page }) => {
    await clickThreadCard(page, 'route-test');
    const robotLink = page.locator('a[href*="robot-settings"]');
    await expect(robotLink).toHaveAttribute('href', /thread=route-test/);
  });

  test('发送按钮将消息添加到对话栈', async ({ page }) => {
    const composer = page.locator('#composer-text');
    await composer.fill('测试消息');
    await page.locator('#composer-send').click();

    const bubbles = page.locator('.bubble');
    await expect(bubbles).toHaveCount(6);
  });

  test('发送热键按钮同样触发发送', async ({ page }) => {
    const composer = page.locator('#composer-text');
    await composer.fill('热键发送测试');
    await page.locator('#composer-send-hotkey').click();

    const bubbles = page.locator('.bubble');
    await expect(bubbles).toHaveCount(6);
  });

  test('空内容发送时弹出 toast 提示', async ({ page }) => {
    const composer = page.locator('#composer-text');
    await composer.fill('');
    await page.locator('#composer-send').click();

    const toast = page.locator('#toast');
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('先输入一点内容再发送');
  });

  test('Ctrl+Enter / Cmd+Enter 发送消息', async ({ page }) => {
    const composer = page.locator('#composer-text');
    await composer.fill('快捷键发送测试');
    await composer.press('Meta+Enter');

    const bubbles = page.locator('.bubble');
    await expect(bubbles).toHaveCount(6);
  });

  test('附加目录按钮插入当前目录名', async ({ page }) => {
    await page.locator('#composer-attach-file').click();

    const composer = page.locator('#composer-text');
    await expect(composer).toContainText('附加目录：src/auth');

    const chip = page.locator('#composer-context-chip');
    await expect(chip).toContainText('已附加');
  });

  test('插入终端输出按钮添加终端占位文本', async ({ page }) => {
    await page.locator('#composer-insert-terminal').click();

    const composer = page.locator('#composer-text');
    await expect(composer).toContainText('终端输出：npm test -- --runInBand 仍待执行');

    const chip = page.locator('#composer-context-chip');
    await expect(chip).toContainText('已插入终端输出占位');
  });


  test('新增会话会在当前目录下创建独立对话', async ({ page }) => {
    const firstAccordion = page.locator('.file-accordion').first();
    await firstAccordion.locator('.new-thread-card').click();

    await expect(page.locator('#composer-thread-chip')).toContainText('会话 3');
    await expect(page.locator('#active-file-title')).toHaveText('src/auth');
    await expect(page.locator('.bubble')).toHaveCount(2);
    await expect(page.locator('.conversation-context-card')).toContainText('新的独立会话');

    await page.locator('.agent-thread-card[data-thread-id="session-review"]').click();
    await expect(page.locator('#composer-thread-chip')).toContainText('review-agent');
    await expect(page.locator('.bubble')).toHaveCount(4);
  });

  test('通过文件夹选择新增关联目录并切换到新会话', async ({ page }) => {
    await page.locator('#related-folder-input').setInputFiles(path.join(process.cwd(), 'tests/fixtures/new-context'));

    await expect(page.locator('#active-file-title')).toHaveText('new-context');
    await expect(page.locator('.file-accordion').first()).toContainText('new-context');
    await expect(page.locator('#composer-thread-chip')).toContainText('primary-agent');
    await expect(page.locator('.conversation-stack')).toContainText('已关联 new-context 目录');
  });


  test('新增会话后进入机器人设置会显示未编排会话角色并可修改', async ({ page }) => {
    const firstAccordion = page.locator('.file-accordion').first();
    await firstAccordion.locator('.new-thread-card').click();

    await page.locator('#robot-settings-link').click();
    await expect(page).toHaveURL(/\/robot-settings\?thread=/);
    await expect(page.locator('#role-count')).toHaveText('3 个');
    await expect(page.locator('#agent-directory')).toContainText('review-agent');
    await expect(page.locator('#agent-directory')).toContainText('auth-agent');
    await expect(page.locator('#agent-directory')).toContainText('会话 3');
    await expect(page.locator('#active-role-name')).toHaveText('会话 3');
    await expect(page.locator('#active-role-tag')).toHaveText('未编排角色');

    await page.locator('#edit-role-description').click();
    await page.locator('#role-name-input').fill('custom-session-agent');
    await page.locator('#role-description-input').fill('已经补齐职责的新增会话角色');
    await page.locator('#modal-save-button').click();

    await expect(page.locator('#active-role-name')).toHaveText('custom-session-agent');
    await expect(page.locator('#selected-thread-label')).toHaveText('custom-session-agent');
  });

  test('线程选择持久化到 localStorage', async ({ page }) => {
    await clickThreadCard(page, 'login-test');

    const stored = await page.evaluate(() =>
      localStorage.getItem('agentDesk.activeThreadId')
    );
    expect(stored).toBe('login-test');
  });

  test('URL query param 优先于 localStorage', async ({ page }) => {
    await page.evaluate(() =>
      localStorage.setItem('agentDesk.activeThreadId', 'route-review')
    );
    await page.goto(`${BASE}/?thread=login-snapshot`);

    const activeCard = page.locator('.agent-thread-card.is-active');
    await expect(activeCard).toHaveAttribute('data-thread-id', 'login-snapshot');
  });
});
