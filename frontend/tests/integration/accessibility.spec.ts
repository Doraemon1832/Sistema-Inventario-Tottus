import { expect, test, request } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const backend = 'http://127.0.0.1:18080';
const routes = ['/login', '/dashboard', '/inventario', '/ingresos', '/egresos', '/compras', '/proveedores', '/kardex'];

test('WCAG 2.2 AA axe scan covers login and all primary modules', async ({ page }) => {
  test.setTimeout(120_000);
  const api = await request.newContext();
  const response = await api.post(`${backend}/api/auth/login`, {
    data: { usuario: process.env.TOTTUS_E2E_USERNAME, password: process.env.TOTTUS_E2E_PASSWORD }
  });
  expect(response.ok()).toBeTruthy();
  const user = await response.json();
  await page.addInitScript(value => {
    localStorage.setItem('tottus_token', value.token);
    localStorage.setItem('tottus_user', JSON.stringify(value));
    localStorage.setItem('tottus_active_sede', String(value.idSede));
  }, user);

  const violations: unknown[] = [];
  for (const route of routes) {
    const navigation = await page.goto(route);
    if (route === '/login') {
      const headers = await navigation?.allHeaders();
      expect(headers?.['content-security-policy']).toContain("script-src 'self'");
      expect(headers?.['x-content-type-options']).toBe('nosniff');
      expect(headers?.['x-frame-options']).toBe('DENY');
    }
    await expect(page.locator('main, .app-layout').first()).toBeVisible();
    const report = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
      .analyze();
    for (const violation of report.violations) {
      violations.push({ route, id: violation.id, impact: violation.impact,
        description: violation.description,
        nodes: violation.nodes.map(node => ({ target: node.target, summary: node.failureSummary,
          html: node.html, relatedNodes: node.any.map(check => (check.relatedNodes ?? []).map(related => related.target)) })) });
    }
  }
  await api.dispose();
  writeFileSync(resolve(process.cwd(), '..', 'auditoria-calidad', 'cierre-implementacion', 'evidencia-axe-2026-11-14.json'),
    JSON.stringify({ generatedAt: new Date().toISOString(), browser: test.info().project.name, engine: 'axe-core', scannedRoutes: routes, tags: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'], violations }, null, 2),
    'utf8');
  expect(violations, JSON.stringify(violations, null, 2)).toEqual([]);
});

test('Kardex is reachable from navigation and its filters work with keyboard focus', async ({ page }) => {
  const api = await request.newContext();
  const response = await api.post(`${backend}/api/auth/login`, {
    data: { usuario: process.env.TOTTUS_E2E_USERNAME, password: process.env.TOTTUS_E2E_PASSWORD }
  });
  expect(response.ok()).toBeTruthy();
  const user = await response.json();
  await page.addInitScript(value => {
    localStorage.setItem('tottus_token', value.token);
    localStorage.setItem('tottus_user', JSON.stringify(value));
    localStorage.setItem('tottus_active_sede', String(value.idSede));
  }, user);
  await page.goto('/inventario');

  const trigger = page.getByRole('link', { name: 'Kardex' });
  await trigger.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Kardex de inventario' })).toBeVisible();
  const typeFilter = page.getByRole('combobox', { name: 'Tipo de movimiento' });
  await typeFilter.focus();
  await expect(typeFilter).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('combobox', { name: 'Producto' })).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(typeFilter).toBeFocused();
  await api.dispose();
});
