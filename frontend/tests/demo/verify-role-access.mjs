import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from '@playwright/test';

const credentialsPath = resolve(process.cwd(), '..', '.local-demo', 'usuarios-demo.local.json');
const { accounts } = JSON.parse((await readFile(credentialsPath, 'utf8')).replace(/^\uFEFF/, ''));
const baseUrl = 'http://127.0.0.1:44201';
const apiUrl = 'http://127.0.0.1:18081';
const expectedModules = {
  ADMINISTRADOR: ['Dashboard', 'Catálogo e inventario', 'Ingresos', 'Egresos', 'Compras', 'Proveedores'],
  JEFE_INVENTARIO: ['Dashboard', 'Catálogo e inventario', 'Ingresos', 'Egresos', 'Compras', 'Proveedores'],
  OPERADOR_ALMACEN: ['Catálogo e inventario', 'Ingresos', 'Egresos'],
  VENDEDOR: ['Catálogo e inventario', 'Egresos'],
  ENCARGADO_COMPRAS: ['Ingresos', 'Compras', 'Proveedores']
};
const modulePaths = {
  Dashboard: '/dashboard',
  'Catálogo e inventario': '/inventario',
  Ingresos: '/ingresos',
  Egresos: '/egresos',
  Compras: '/compras',
  Proveedores: '/proveedores'
};

const browser = await chromium.launch({ headless: true });
try {
  for (const account of accounts) {
    const loginResponse = await fetch(`${apiUrl}/api/auth/login`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ usuario: account.username, password: account.password })
    });
    assert.equal(loginResponse.status, 200, `${account.role}: login debe ser aceptado`);
    const user = await loginResponse.json();
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    await page.addInitScript(value => {
      localStorage.setItem('tottus_token', value.token);
      localStorage.setItem('tottus_user', JSON.stringify(value));
      localStorage.setItem('tottus_active_sede', String(value.idSede));
    }, user);
    await page.goto(`${baseUrl}/inventario`);
    const navigation = page.getByRole('navigation', { name: 'Módulos del sistema' });
    for (const moduleName of ['Dashboard', 'Catálogo e inventario', 'Ingresos', 'Egresos', 'Compras', 'Proveedores']) {
      const visible = await navigation.getByRole('link', { name: moduleName, exact: true }).count() > 0;
      assert.equal(visible, expectedModules[account.role].includes(moduleName), `${account.role}: permiso visible de ${moduleName}`);
    }
    const fallbackPath = account.role === 'ENCARGADO_COMPRAS' ? '/compras' : '/inventario';
    for (const [moduleName, path] of Object.entries(modulePaths)) {
      await page.goto(`${baseUrl}${path}`);
      const expectedPath = expectedModules[account.role].includes(moduleName) ? path : fallbackPath;
      assert.equal(new URL(page.url()).pathname, expectedPath, `${account.role}: ruta ${path}`);
      if (moduleName === 'Dashboard' && expectedPath === path) {
        await page.getByRole('heading', { name: 'Dashboard de inventario' }).waitFor({ state: 'visible' });
      }
    }
    await context.close();
    console.log(`${account.role}: login, menú por rol y ruta protegida verificados`);
  }
} finally {
  await browser.close();
}
