import { expect, test } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import { join } from 'node:path';

const evidenceDir = process.env.TOTTUS_MAIN_EVIDENCE_DIR;
const smokeUser = process.env.TOTTUS_MAIN_SMOKE_USER
  ? JSON.parse(process.env.TOTTUS_MAIN_SMOKE_USER)
  : undefined;
const token = process.env.TOTTUS_MAIN_SMOKE_TOKEN;

test('main :4200 serves the approved login and fits the supported viewport widths', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 900 });
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'TOTTUS' })).toBeVisible();
  await expect(page.getByLabel('USUARIO')).toBeVisible();
  await expect(page.getByLabel(/CONTRASEÑA/i)).toBeVisible();

  if (evidenceDir) {
    await mkdir(evidenceDir, { recursive: true });
    await page.screenshot({ path: join(evidenceDir, 'main-4200-login.png'), fullPage: true });
  }

  for (const width of [375, 390, 768, 1024, 1280, 1366, 1440, 1600, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(overflow, `horizontal page overflow at ${width}px`).toBeFalsy();
  }
});

test('main :4200 loads protected feature routes and reads them from the Compose API', async ({ page }) => {
  if (!token || !smokeUser) {
    test.skip(true, 'requires a short-lived read-only smoke JWT generated from the local Compose configuration');
    return;
  }
  const apiResponses: Array<{ path: string; status: number; sedeId: string | null }> = [];
  page.on('response', response => {
    const url = new URL(response.url());
    if (url.pathname.startsWith('/api/')) apiResponses.push({
      path: url.pathname,
      status: response.status(),
      sedeId: url.searchParams.get('sedeId')
    });
  });
  await page.addInitScript(({ jwt, user }) => {
    localStorage.setItem('tottus_token', jwt);
    localStorage.setItem('tottus_user', JSON.stringify(user));
    if (!localStorage.getItem('tottus_active_sede')) {
      localStorage.setItem('tottus_active_sede', String(user.idSede));
    }
  }, { jwt: token, user: smokeUser });

  const routes = [
    { path: '/dashboard', heading: 'Dashboard de inventario', api: '/api/inventario/resumen' },
    { path: '/inventario', api: '/api/inventario' },
    { path: '/kardex', heading: 'Kardex de inventario', api: '/api/inventario/movimientos' },
    { path: '/ingresos', heading: 'Ingresos', api: '/api/operaciones/ingresos' },
    { path: '/egresos', heading: 'Egresos', api: '/api/operaciones/egresos' },
    { path: '/compras', heading: 'Compras', api: '/api/compras' },
    { path: '/proveedores', heading: 'Proveedores', api: '/api/proveedores' }
  ];

  for (const route of routes) {
    const firstResponse = apiResponses.length;
    await page.goto(route.path);
    if ('heading' in route) await expect(page.getByRole('heading', { name: route.heading }).first()).toBeVisible();
    else await expect(page.locator('.inventario-page')).toBeVisible();
    await expect.poll(() => apiResponses.slice(firstResponse).some(item => item.path === route.api && item.status === 200), {
      message: `${route.path} should read ${route.api} through the main frontend proxy`
    }).toBeTruthy();
  }

  await page.goto('/dashboard');
  const siteSelector = page.getByRole('combobox', { name: 'Sede activa' });
  if (smokeUser.rol === 'ADMINISTRADOR' && smokeUser.accesoGlobal === true) {
    await expect(siteSelector).toBeVisible();
    await expect(siteSelector.locator('option')).toHaveCount(smokeUser.idSedesAutorizadas.length);
    const originalSite = String(smokeUser.idSede);
    const alternateSite = smokeUser.idSedesAutorizadas.find((id: number) => id !== smokeUser.idSede);
    expect(alternateSite).toBeTruthy();
    const siteResponseCount = apiResponses.length;
    await Promise.all([
      page.waitForNavigation(),
      siteSelector.selectOption(String(alternateSite))
    ]);
    await expect.poll(() => page.evaluate(() => localStorage.getItem('tottus_active_sede'))).toBe(String(alternateSite));
    await expect.poll(() => apiResponses.slice(siteResponseCount).some(item =>
      item.path.startsWith('/api/inventario/resumen') && item.status === 200 && item.sedeId === String(alternateSite))).toBeTruthy();
    await page.evaluate(id => localStorage.setItem('tottus_active_sede', id), originalSite);
    await page.reload();
    await expect(siteSelector).toHaveValue(originalSite);
  } else {
    await expect(siteSelector).toHaveCount(0);
    await expect(page.locator('.sede-value')).toBeVisible();
    await expect(page.locator('.sede-value')).toContainText(smokeUser.nombreSede);
  }
  await expect(page.getByRole('heading', { name: 'Dashboard de inventario' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Accesos directos' })).toHaveCount(0);
  await expect(page.locator('.metric-card')).toHaveCount(6);
  if (evidenceDir) await page.screenshot({ path: join(evidenceDir, 'main-4200-dashboard.png'), fullPage: true });
  const periodResponseCount = apiResponses.filter(item => item.path === '/api/operaciones/ingresos' || item.path === '/api/operaciones/egresos').length;
  await page.getByRole('button', { name: '30 días' }).click();
  await expect.poll(() => apiResponses.filter(item => item.path === '/api/operaciones/ingresos' || item.path === '/api/operaciones/egresos').length)
    .toBeGreaterThan(periodResponseCount);
  await expect.poll(() => ['ingresos', 'egresos'].every(type =>
    apiResponses.some(item => item.path === `/api/operaciones/${type}` && item.status === 200))).toBeTruthy();

  await page.goto('/kardex');
  await expect(page.getByRole('heading', { name: 'Kardex de inventario' })).toBeVisible();
  await expect.poll(() => apiResponses.some(item => item.path === '/api/inventario/movimientos' && item.status === 200)).toBeTruthy();
  if (evidenceDir) await page.screenshot({ path: join(evidenceDir, 'main-4200-kardex.png'), fullPage: true });
  await page.goto('/inventario');
  if (evidenceDir) await page.screenshot({ path: join(evidenceDir, 'main-4200-inventory.png'), fullPage: true });
  for (const width of [375, 390, 768, 1024, 1280, 1366, 1440, 1600, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    const layout = await page.evaluate(() => {
      const shell = document.querySelector<HTMLElement>('.table-responsive');
      const headers = shell?.querySelectorAll<HTMLElement>('thead th') ?? [];
      return {
        pageOverflows: document.documentElement.scrollWidth > window.innerWidth,
        tableOverflows: !!shell && shell.scrollWidth > shell.clientWidth + 2,
        overflowingHeaders: Array.from(headers).filter(cell => cell.scrollWidth > cell.clientWidth + 2).map(cell => cell.textContent?.trim())
      };
    });
    expect(layout.pageOverflows, `inventory page horizontal overflow at ${width}px`).toBeFalsy();
    if (width >= 1280) {
      expect(layout.tableOverflows, `desktop inventory table overflows at ${width}px`).toBeFalsy();
      expect(layout.overflowingHeaders, `desktop inventory header text overlaps at ${width}px`).toEqual([]);
    }
  }

  await page.setViewportSize({ width: 375, height: 812 });
  const menuButton = page.getByRole('button', { name: 'Abrir menú' });
  await menuButton.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#primary-navigation.open')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible();
});
