import { expect, test, request } from '@playwright/test';

const backend = 'http://127.0.0.1:18080';

test('catalogo limita a editar, ajustar stock y cambiar estado; ajuste conserva stock final absoluto', async ({ page, request: apiRequest }) => {
  const nativeDialogs: string[] = [];
  page.on('dialog', dialog => { nativeDialogs.push(dialog.type()); void dialog.dismiss(); });
  const api = await request.newContext();
  const login = await api.post(`${backend}/api/auth/login`, {
    data: { usuario: process.env.TOTTUS_E2E_USERNAME, password: process.env.TOTTUS_E2E_PASSWORD }
  });
  expect(login.ok()).toBeTruthy();
  const user = await login.json();
  await page.addInitScript(value => {
    localStorage.setItem('tottus_token', value.token);
    localStorage.setItem('tottus_user', JSON.stringify(value));
    localStorage.setItem('tottus_active_sede', String(value.idSede));
  }, user);
  await page.goto('/inventario');
  const row = page.locator('.inventario-table tbody tr:not(.row-inactivo)').first();
  await expect(row).toBeVisible();
  const actions = row.locator('.action-btn-group button');
  await expect(actions).toHaveCount(3);
  await expect(actions.nth(0)).toHaveAttribute('aria-label', 'Editar producto');
  await expect(actions.nth(1)).toHaveAttribute('aria-label', 'Ajustar stock');
  await expect(actions.nth(2)).toHaveAttribute('aria-label', 'Inactivar producto');
  await expect(row.getByRole('button', { name: /Registrar entrada|Registrar salida/i })).toHaveCount(0);

  const sku = (await row.locator('.cell-sku').innerText()).trim();
  const inventory = await (await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, {
    headers: { Authorization: `Bearer ${user.token}` }
  })).json();
  const product = inventory.find((item: any) => item.codigoProducto === sku);
  expect(product).toBeTruthy();
  const targetStock = product.stockActual + 2;
  await actions.nth(1).click();
  const movementDialog = page.getByRole('dialog', { name: 'Ajustar inventario' });
  await expect(movementDialog).toBeVisible();
  await expect(movementDialog).toContainText(product.codigoProducto);
  await expect(movementDialog).toContainText(product.nombreProducto);
  await expect(movementDialog).toContainText(String(product.stockActual));
  await expect(movementDialog.getByRole('combobox')).toHaveCount(0);
  await expect(movementDialog.locator('.stock-calc-preview')).toBeInViewport();
  const stockInput = page.getByLabel('Nuevo stock final (valor absoluto)');
  await expect(stockInput).toHaveValue(String(product.stockActual));
  await stockInput.fill(String(targetStock));
  const reference = 'PW-ABS-STOCK-' + Date.now();
  await page.getByLabel('Referencia documental / motivo').fill(reference);
  const writes: string[] = [];
  page.on('request', outgoing => {
    if (outgoing.method() === 'POST' && outgoing.url().endsWith('/api/inventario/movimientos')) writes.push(outgoing.url());
  });
  await page.getByRole('button', { name: 'Revisar ajuste' }).click();
  const confirmation = page.getByRole('dialog', { name: 'Confirmar ajuste de inventario' });
  await expect(confirmation).toContainText(`Stock actual${product.stockActual} unidades`);
  await expect(confirmation).toContainText(`Stock final${targetStock} unidades`);
  await expect(confirmation).toContainText(`Diferencia${targetStock - product.stockActual} unidades`);
  expect(writes).toHaveLength(0);
  await confirmation.getByRole('button', { name: 'Cancelar' }).click();
  await expect(movementDialog).toBeVisible();
  await expect(page.getByRole('button', { name: 'Revisar ajuste' })).toBeFocused();
  await expect(stockInput).toHaveValue(String(targetStock));
  expect(writes).toHaveLength(0);

  const responsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().endsWith('/api/inventario/movimientos'));
  await page.getByRole('button', { name: 'Revisar ajuste' }).click();
  await page.getByRole('dialog', { name: 'Confirmar ajuste de inventario' }).getByRole('button', { name: 'Confirmar ajuste' }).click();
  const response = await responsePromise;
  expect(response.ok(), await response.text()).toBeTruthy();

  const after = await (await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, {
    headers: { Authorization: `Bearer ${user.token}` }
  })).json();
  expect(after.find((item: any) => item.idProducto === product.idProducto).stockActual).toBe(targetStock);
  const kardex = await (await apiRequest.get(`${backend}/api/inventario/movimientos?sedeId=${user.idSede}&page=0&size=50`, {
    headers: { Authorization: `Bearer ${user.token}` }
  })).json();
  const movement = kardex.content.find((item: any) => item.referencia === reference);
  expect(movement).toBeTruthy();
  expect(movement.tipo).toBe('AJUSTE');
  expect(movement.cantidad).toBe(targetStock);
  expect(nativeDialogs).toEqual([]);
  await api.dispose();
});
