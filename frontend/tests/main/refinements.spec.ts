import { expect, test } from '@playwright/test';
import { mkdir, readFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';

const credentialFile = process.env.TOTTUS_ROLE_CREDENTIALS_FILE;
const evidenceDirectory = resolve(process.cwd(), '..', '.local-demo', 'evidence-4200', 'refinements');

test('main :4200 muestra acciones de catálogo y confirmaciones Angular sin escribir operaciones', async ({ page }) => {
  test.skip(!credentialFile, 'Requires locally stored role credentials outside Git.');
  const credentials = await readFile(credentialFile!, 'utf8');
  const admin = credentials.split(/\r?\n/).map(line => line.split('|').map(value => value.trim()))
    .find(([role]) => role === 'ADMINISTRADOR');
  expect(admin).toBeTruthy();

  const nativeDialogs: string[] = [];
  const businessWrites: string[] = [];
  page.on('dialog', dialog => { nativeDialogs.push(dialog.type()); void dialog.dismiss(); });
  page.on('request', outgoing => {
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(outgoing.method()) && /\/api\/(operaciones|inventario\/movimientos)/.test(outgoing.url())) {
      businessWrites.push(`${outgoing.method()} ${outgoing.url()}`);
    }
  });
  await page.goto('/login');
  await page.getByLabel('USUARIO').fill(admin![1]);
  await page.getByLabel('CONTRASEÑA').fill(admin![2]);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL('**/dashboard');
  await mkdir(evidenceDirectory, { recursive: true });

  await page.goto('/inventario');
  const stockedSku = await page.evaluate(async () => {
    const user = JSON.parse(localStorage.getItem('tottus_user') || '{}');
    const token = localStorage.getItem('tottus_token');
    const response = await fetch(`/api/inventario?sedeId=${user.idSede}`, { headers: { Authorization: `Bearer ${token}` } });
    const inventory = await response.json();
    return inventory.find((item: any) => item.estado && item.stockActual > 0)?.codigoProducto as string | undefined;
  });
  expect(stockedSku).toBeTruthy();
  const row = page.locator('.inventario-table tbody tr:not(.row-inactivo)').first();
  await expect(row).toBeVisible();
  const actions = row.locator('.action-btn-group button');
  await expect(actions).toHaveCount(3);
  await expect(actions.nth(0)).toHaveAttribute('aria-label', 'Editar producto');
  await expect(actions.nth(1)).toHaveAttribute('aria-label', 'Ajustar stock');
  await expect(actions.nth(2)).toHaveAttribute('aria-label', 'Inactivar producto');
  await actions.nth(1).click();
  const adjustment = page.getByRole('dialog', { name: 'Ajustar inventario' });
  await expect(adjustment).toBeVisible();
  await expect(page.getByLabel('Nuevo stock final (valor absoluto)')).toBeVisible();
  await page.screenshot({ path: join(evidenceDirectory, 'main-4200-ajuste-stock.png'), fullPage: true });
  await adjustment.getByRole('button', { name: 'Cancelar' }).click();

  for (const [path, productName, confirmTitle, reviewName, screenshot] of [
    ['/ingresos', 'Ingresos de inventario', 'Confirmar ingreso de inventario', 'Registrar ingreso', 'main-4200-confirmar-ingreso.png'],
    ['/egresos', 'Egresos de inventario', 'Confirmar egreso de inventario', 'Registrar egreso', 'main-4200-confirmar-egreso.png']
  ]) {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: productName })).toBeVisible();
    await page.getByLabel('Referencia documental').fill(`SMOKE-${Date.now()}`);
    const product = page.locator('.product-picker input[role="combobox"]').first();
    await product.fill(stockedSku!);
    await expect(page.locator('.product-picker [role="option"]').first()).toBeVisible();
    await product.press('ArrowDown');
    await product.press('Enter');
    await page.locator('.quantity-field input').first().fill('1');
    await page.getByRole('button', { name: reviewName }).click();
    const confirmation = page.getByRole('dialog', { name: confirmTitle });
    await expect(confirmation).toBeVisible();
    await page.screenshot({ path: join(evidenceDirectory, screenshot), fullPage: true });
    await confirmation.getByRole('button', { name: 'Cancelar' }).click();
    await expect(confirmation).toHaveCount(0);
    expect(businessWrites).toEqual([]);
  }

  for (const width of [375, 390, 768, 1024, 1280, 1366, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(overflow, `page overflow at ${width}px`).toBeFalsy();
  }
  expect(nativeDialogs).toEqual([]);
  expect(businessWrites).toEqual([]);
});
