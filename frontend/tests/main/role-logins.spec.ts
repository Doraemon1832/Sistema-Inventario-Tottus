import { expect, test } from '@playwright/test';
import { mkdir, readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const credentialFile = process.env.TOTTUS_ROLE_CREDENTIALS_FILE;
const expectedModules: Record<string, string[]> = {
  ADMINISTRADOR: ['/dashboard', '/inventario', '/ingresos', '/egresos', '/compras', '/proveedores', '/kardex'],
  JEFE_INVENTARIO: ['/dashboard', '/inventario', '/ingresos', '/egresos', '/compras', '/proveedores', '/kardex'],
  OPERADOR_ALMACEN: ['/dashboard', '/inventario', '/ingresos', '/egresos'],
  VENDEDOR: ['/dashboard', '/inventario', '/egresos'],
  ENCARGADO_COMPRAS: ['/dashboard', '/ingresos', '/compras', '/proveedores']
};
const dashboardTitles: Record<string, string> = {
  ADMINISTRADOR: 'Resumen general autorizado', JEFE_INVENTARIO: 'Supervisión de inventario',
  OPERADOR_ALMACEN: 'Actividad del almacén', VENDEDOR: 'Disponibilidad y salidas',
  ENCARGADO_COMPRAS: 'Abastecimiento y compras'
};

test('cinco cuentas reales inician sesión, reciben solo sus módulos y cargan su dashboard', async ({ page }) => {
  test.skip(!credentialFile, 'Define TOTTUS_ROLE_CREDENTIALS_FILE para ejecutar el smoke local con credenciales fuera de Git.');
  const contents = await readFile(credentialFile!, 'utf8');
  const credentials = new Map<string, { username: string; password: string }>();
  for (const line of contents.split(/\r?\n/)) {
    const [role, username, password] = line.split('|').map(value => value.trim());
    if (role && username && password && expectedModules[role]) credentials.set(role, { username, password });
  }
  expect([...credentials.keys()].sort()).toEqual(Object.keys(expectedModules).sort());

  const screenshotDirectory = resolve(process.cwd(), '..', '.local-demo', 'evidence-4200', 'roles');
  await mkdir(screenshotDirectory, { recursive: true });
  const forbiddenResponses: string[] = [];
  page.on('response', response => {
    if (response.url().includes('/api/') && response.status() === 403) forbiddenResponses.push(response.url());
  });

  for (const [role, modules] of Object.entries(expectedModules)) {
    const account = credentials.get(role)!;
    await page.goto('/login');
    await page.getByLabel('USUARIO').fill(account.username);
    await page.getByLabel('CONTRASEÑA').fill(account.password);
    const loginResponse = page.waitForResponse(response => response.url().endsWith('/api/auth/login') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Ingresar' }).click();
    const submittedLogin = await loginResponse;
    expect(submittedLogin.status(), `${role} login endpoint rejected the submitted form`).toBe(200);
    await page.waitForURL('**/dashboard');
    await expect(page.getByRole('heading', { name: 'Dashboard de inventario' })).toBeVisible();
    await expect(page.getByText(dashboardTitles[role], { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible();
    for (const route of ['/dashboard', '/inventario', '/ingresos', '/egresos', '/compras', '/proveedores', '/kardex']) {
      const link = page.locator(`.nav-groups a[href="${route}"]`);
      if (modules.includes(route)) await expect(link).toBeVisible();
      else await expect(link).toHaveCount(0);
    }
    await expect(page.locator('.loading-state')).toHaveCount(0);
    expect(forbiddenResponses, `${role} made a forbidden dashboard request`).toEqual([]);
    await page.screenshot({ path: resolve(screenshotDirectory, `${role.toLowerCase()}.png`), fullPage: true });

    for (const route of modules.filter(module => module !== '/dashboard')) {
      const previousForbiddenCount = forbiddenResponses.length;
      await page.goto(route);
      await page.waitForURL(`**${route}`);
      await expect(page.locator('app-inventario, app-operaciones, app-abastecimiento, app-kardex, app-dashboard')).toHaveCount(1);
      expect(forbiddenResponses.slice(previousForbiddenCount), `${role} made a forbidden request in ${route}`).toEqual([]);
    }

    if (role === 'ENCARGADO_COMPRAS') {
      await page.goto('/inventario');
      await page.waitForURL('**/compras');
    } else if (role === 'OPERADOR_ALMACEN') {
      await page.goto('/compras');
      await page.waitForURL('**/inventario');
    } else if (role === 'VENDEDOR') {
      await page.goto('/compras');
      await page.waitForURL('**/inventario');
    }
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await page.waitForURL('**/login');
  }
});
