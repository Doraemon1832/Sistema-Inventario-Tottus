import { expect, test } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

type LocalCredential = { role: string; username: string; password: string };

const credentialFile = resolve(process.cwd(), '..', '.local-demo', 'role-credentials-2026-11-18.txt');
const localCredentials = (existsSync(credentialFile) ? readFileSync(credentialFile, 'utf8') : '').split(/\r?\n/).flatMap(line => {
  const parts = line.split('|').map(part => part.trim());
  return parts.length === 3 && parts[0] && parts[1] && parts[2]
    ? [{ role: parts[0], username: parts[1], password: parts[2] }]
    : [];
});

const roleCases = [
  { role: 'ADMINISTRADOR', allowed: ['Dashboard', 'Catálogo e inventario', 'Ingresos', 'Egresos', 'Compras', 'Proveedores', 'Kardex'] },
  { role: 'JEFE_INVENTARIO', allowed: ['Dashboard', 'Catálogo e inventario', 'Ingresos', 'Egresos', 'Compras', 'Proveedores', 'Kardex'] },
  { role: 'OPERADOR_ALMACEN', allowed: ['Dashboard', 'Catálogo e inventario', 'Ingresos', 'Egresos'] },
  { role: 'VENDEDOR', allowed: ['Dashboard', 'Catálogo e inventario', 'Egresos'] },
  { role: 'ENCARGADO_COMPRAS', allowed: ['Dashboard', 'Ingresos', 'Compras', 'Proveedores'] }
];

for (const expected of roleCases) {
  test(`login local y menú por rol: ${expected.role}`, async ({ page }) => {
    const credential = localCredentials.find(item => item.role === expected.role);
    test.skip(!credential, `No hay credencial local configurada para ${expected.role}`);
    if (!credential) return;

    await page.goto('http://localhost:4200/login');
    await page.getByLabel('USUARIO').fill(credential!.username);
    await page.locator('#password-input').fill(credential!.password);
    await page.getByRole('button', { name: /Ingresar/ }).click();

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByText(expected.role, { exact: true })).toBeVisible();
    for (const moduleName of expected.allowed) {
      await expect(page.getByRole('link', { name: moduleName, exact: true })).toBeVisible();
    }
    for (const moduleName of ['Kardex', 'Compras', 'Proveedores', 'Egresos', 'Ingresos', 'Catálogo e inventario']) {
      if (!expected.allowed.includes(moduleName)) {
        await expect(page.getByRole('link', { name: moduleName, exact: true })).toHaveCount(0);
      }
    }

    if (expected.role === 'ENCARGADO_COMPRAS') {
      await expect(page.getByText('Compras pendientes', { exact: true })).toBeVisible();
      await expect(page.getByText('Proveedores activos', { exact: true })).toBeVisible();
    }
    if (expected.role === 'OPERADOR_ALMACEN') {
      await expect(page.getByText('Actividad del almacén', { exact: true })).toBeVisible();
    }
    if (expected.role === 'VENDEDOR') {
      await expect(page.getByText('Disponibilidad y salidas', { exact: true })).toBeVisible();
    }
    if (expected.role === 'ADMINISTRADOR') {
      await expect(page.getByText('Resumen general autorizado', { exact: true })).toBeVisible();
    }
    if (expected.role === 'JEFE_INVENTARIO') {
      await expect(page.getByText('Supervisión de inventario', { exact: true })).toBeVisible();
    }

    if (['ADMINISTRADOR', 'JEFE_INVENTARIO', 'OPERADOR_ALMACEN', 'VENDEDOR'].includes(expected.role)) {
      await page.getByRole('link', { name: 'Catálogo e inventario', exact: true }).click();
      await expect(page.locator('.table-card')).toBeVisible();
      if (expected.role === 'JEFE_INVENTARIO') {
        await expect(page.getByRole('columnheader', { name: 'ACCIONES' })).toHaveCount(0);
        await expect(page.getByRole('button', { name: 'Editar producto' })).toHaveCount(0);
      } else if (expected.role === 'VENDEDOR') {
        await expect(page.getByRole('columnheader', { name: 'ACCIONES' })).toHaveCount(0);
        await expect(page.getByRole('button', { name: 'Editar producto' })).toHaveCount(0);
      } else {
        await expect(page.getByRole('columnheader', { name: 'ACCIONES' })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Editar producto' }).first()).toBeVisible();
        if (expected.role === 'OPERADOR_ALMACEN') await expect(page.getByRole('button', { name: 'Ajustar stock' })).toHaveCount(0);
        if (expected.role === 'ADMINISTRADOR') await expect(page.getByRole('button', { name: 'Ajustar stock' }).first()).toBeVisible();
      }
    }

    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await expect(page).toHaveURL(/\/login$/);
  });
}
