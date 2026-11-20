import { expect, test, request } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';

const backend = 'http://127.0.0.1:18080';
const outputDirectory = resolve(process.cwd(), '..', 'auditoria-calidad', 'cierre-implementacion', 'capturas-visuales');

test('capturas reales de pantallas, estados y formularios a 100% y en móvil', async ({ page, browserName }) => {
  test.skip(browserName !== 'chromium', 'Se conserva un set de referencia único en Chromium.');
  test.setTimeout(90_000);
  await mkdir(outputDirectory, { recursive: true });

  await page.setViewportSize({ width: 1440, height: 960 });
  const capture = async (file: string) => page.screenshot({ path: resolve(outputDirectory, file), fullPage: true, animations: 'disabled' });

  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Ingresar' })).toBeVisible();
  await capture('01-login.png');

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

  await page.goto('/dashboard');
  await expect(page.getByRole('heading', { name: 'Dashboard de inventario' })).toBeVisible();
  await expect(page.locator('.metrics-grid')).toBeVisible();
  await capture('02-dashboard-menu-expandido.png');

  await page.goto('/inventario');
  await expect(page.locator('.inventario-table')).toBeVisible();
  await capture('03-catalogo-inventario-1440.png');
  await page.getByRole('button', { name: 'Contraer menú' }).click();
  await capture('04-menu-contraido.png');
  await page.getByRole('button', { name: 'Expandir menú' }).click();
  await page.getByRole('button', { name: 'Nuevo Producto' }).click();
  await expect(page.getByRole('dialog', { name: /Dar de Alta Nuevo Producto en Inventario/i })).toBeVisible();
  await capture('05-formulario-producto.png');
  await page.keyboard.press('Escape');

  await page.goto('/ingresos');
  await expect(page.getByRole('heading', { name: 'Ingresos de inventario' })).toBeVisible();
  await capture('06-ingresos-formulario.png');
  await page.goto('/egresos');
  await expect(page.getByRole('heading', { name: 'Egresos de inventario' })).toBeVisible();
  await capture('07-egresos-formulario.png');

  await page.goto('/kardex');
  await expect(page.getByRole('heading', { name: /Kardex de inventario/i })).toBeVisible();
  await capture('08-kardex.png');

  await page.goto('/compras');
  await expect(page.getByRole('heading', { name: /Registrar compra/ })).toBeVisible();
  await capture('09-compras-formulario.png');
  await page.goto('/proveedores');
  await expect(page.getByRole('heading', { name: 'Proveedores', exact: true })).toBeVisible();
  await capture('10-proveedores-formulario.png');

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/dashboard');
  await expect(page.locator('.metrics-grid')).toBeVisible();
  await capture('11-dashboard-movil-390.png');
  await page.getByRole('button', { name: 'Abrir menú' }).click();
  await expect(page.locator('.side-nav')).toHaveClass(/open/);
  await capture('12-menu-drawer-movil-390.png');
  await api.dispose();
});
