import { expect, test, request } from '@playwright/test';

const backend = 'http://127.0.0.1:18080';

async function authenticate(page: import('@playwright/test').Page) {
  const api = await request.newContext();
  const response = await api.post(`${backend}/api/auth/login`, {
    data: { usuario: process.env.TOTTUS_E2E_USERNAME, password: process.env.TOTTUS_E2E_PASSWORD }
  });
  expect(response.ok()).toBeTruthy();
  const user = await response.json();
  await page.addInitScript((value) => {
    localStorage.setItem('tottus_token', value.token);
    localStorage.setItem('tottus_user', JSON.stringify(value));
    localStorage.setItem('tottus_active_sede', String(value.idSede));
  }, user);
  await api.dispose();
  return user;
}

test('HU23-HU30: proveedor y compra pendiente respetan edición, duplicados, inactivación y recepción', async ({ page, request: api }) => {
  const user = await authenticate(page);
  const suffix = `${Date.now()}`;
  const document = `${String(Date.now()).slice(-8)}${String(Math.floor(Math.random() * 1000)).padStart(3, '0')}`;
  const supplierName = `Proveedor E2E ${suffix}`;
  const supplierNameEdited = `${supplierName} editado`;
  const beforeInventory = await (await api.get(`${backend}/api/inventario?sedeId=${user.idSede}`, {
    headers: { Authorization: `Bearer ${user.token}` }
  })).json();
  const product = beforeInventory.find((item: any) => item.estado && item.stockActual >= 0);
  expect(product).toBeTruthy();
  const baselineStock = product.stockActual;

  await page.goto('/proveedores');
  await page.getByLabel('RUC').fill(document);
  await page.getByLabel('Razón social / Nombre de la empresa').fill(supplierName);
  await page.getByRole('button', { name: 'Guardar proveedor' }).click();
  await expect(page.getByText(supplierName)).toBeVisible();

  const supplierRow = page.locator('article.row').filter({ hasText: supplierName });
  await supplierRow.getByRole('button', { name: 'Editar' }).click();
  await expect(page.getByRole('heading', { name: 'Editar proveedor' })).toBeVisible();
  await page.getByLabel('Razón social / Nombre de la empresa').fill(supplierNameEdited);
  await expect(page.getByLabel('Razón social / Nombre de la empresa')).toHaveValue(supplierNameEdited);
  const supplierEditResponse = page.waitForResponse(response => response.url().includes('/api/proveedores/') && response.request().method() === 'PUT');
  await page.getByRole('button', { name: 'Guardar proveedor' }).click();
  expect((await supplierEditResponse).ok()).toBeTruthy();
  const editedSupplierResponse = await api.get(`${backend}/api/proveedores?query=${encodeURIComponent(document)}`, {
    headers: { Authorization: `Bearer ${user.token}` }
  });
  const editedSupplier = await editedSupplierResponse.json().then((items: any[]) => items.find((item) => item.documento === document));
  expect(editedSupplier?.nombre).toBe(supplierNameEdited);

  await page.getByLabel('RUC').fill(document);
  await page.getByLabel('Razón social / Nombre de la empresa').fill(`Duplicado ${suffix}`);
  await page.getByRole('button', { name: 'Guardar proveedor' }).click();
  await expect(page.getByRole('alert')).toContainText(/documento|proveedor/i);
  await page.getByLabel('Razón social / Nombre de la empresa').fill('');

  await page.goto('/compras');
  await page.getByLabel('Referencia').fill(`PW-PO-${suffix}`);
  await page.getByLabel('Proveedor').selectOption({ label: supplierNameEdited });
  await page.locator('.detail select').first().selectOption({
    label: `${product.codigoProducto} · ${product.nombreProducto}`
  });
  await page.locator('.detail input[type="number"]').first().fill('2');
  await page.getByRole('button', { name: 'Guardar compra' }).click();
  await expect(page.getByText(`PW-PO-${suffix}`)).toBeVisible();
  let currentInventory = await (await api.get(`${backend}/api/inventario?sedeId=${user.idSede}`, {
    headers: { Authorization: `Bearer ${user.token}` }
  })).json();
  expect(currentInventory.find((item: any) => item.idProducto === product.idProducto).stockActual).toBe(baselineStock);

  const purchase = page.locator('article.purchase').filter({ hasText: `PW-PO-${suffix}` });
  await purchase.getByRole('button', { name: 'Editar' }).click();
  await expect(page.getByRole('heading', { name: 'Editar compra pendiente' })).toBeVisible();
  const quantityInput = page.locator('.detail input[type="number"]').first();
  await expect(quantityInput).toHaveValue('2');
  await quantityInput.fill('3');
  await expect(quantityInput).toHaveValue('3');
  const editRequest = page.waitForRequest(request => request.url().includes('/api/compras/') && request.method() === 'PUT');
  const editResponse = page.waitForResponse(response => response.url().includes('/api/compras/') && response.request().method() === 'PUT');
  await page.getByRole('button', { name: 'Guardar compra' }).click();
  expect(JSON.parse((await editRequest).postData()!).detalles[0].cantidad).toBe(3);
  const editResult = await editResponse;
  expect(editResult.ok()).toBeTruthy();
  const editedPurchase = await editResult.json();
  expect(editedPurchase.detalles.find((line: any) => line.idProducto === product.idProducto).cantidad).toBe(3);
  await expect(page.locator('article.purchase').filter({ hasText: `PW-PO-${suffix}` }).locator('small')).toContainText('3');

  await page.locator('article.purchase').filter({ hasText: `PW-PO-${suffix}` }).getByRole('button', { name: 'Registrar recepción' }).click();
  await expect(page.getByRole('dialog', { name: 'Confirmar recepción de compra' })).toContainText(`PW-PO-${suffix}`);
  await page.getByRole('button', { name: 'Confirmar recepción' }).click();
  await expect(page.locator('article.purchase').filter({ hasText: `PW-PO-${suffix}` })).toContainText('RECIBIDA');
  currentInventory = await (await api.get(`${backend}/api/inventario?sedeId=${user.idSede}`, {
    headers: { Authorization: `Bearer ${user.token}` }
  })).json();
  expect(currentInventory.find((item: any) => item.idProducto === product.idProducto).stockActual).toBe(baselineStock + 3);

  const supplierResponse = await api.get(`${backend}/api/proveedores?query=${encodeURIComponent(document)}`, {
    headers: { Authorization: `Bearer ${user.token}` }
  });
  const supplier = await supplierResponse.json().then((items: any[]) => items.find((item) => item.nombre === supplierNameEdited));
  expect(supplier).toBeTruthy();
  await page.goto('/proveedores');
  await page.locator('article.row').filter({ hasText: supplierNameEdited }).getByRole('button', { name: 'Inactivar' }).click();
  await expect(page.getByRole('dialog', { name: 'Inactivar proveedor' })).toBeVisible();
  await expect(page.getByRole('dialog')).toContainText(document);
  await page.getByRole('button', { name: 'Confirmar inactivación' }).click();
  await expect(page.locator('article.row').filter({ hasText: supplierNameEdited })).toContainText('Inactivo');
  const inactivePurchase = await api.post(`${backend}/api/compras`, {
    headers: { Authorization: `Bearer ${user.token}` },
    data: { idSede: user.idSede, idProveedor: supplier.idProveedor, referencia: `PW-PO-INACTIVE-${suffix}`,
      detalles: [{ idProducto: product.idProducto, cantidad: 1 }] }
  });
  expect(inactivePurchase.status()).toBe(400);
});
