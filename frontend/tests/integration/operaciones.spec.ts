import { expect, test, request } from '@playwright/test';

const backend = 'http://127.0.0.1:18080';

test('HU01: encargado de compras entra a ingresos usando catálogo sin consultar stock', async ({ page }) => {
  const apiCalls: string[] = [];
  await page.route('**/api/**', async route => {
    apiCalls.push(new URL(route.request().url()).pathname);
    const path = new URL(route.request().url()).pathname;
    const body = path === '/api/productos'
      ? [{ idProducto: 42, codigo: 'SKU-42', nombre: '<img src=x onerror=alert(1)>', precio: 1, stockMinimo: 0, estado: true }]
      : path === '/api/operaciones/ingresos' ? [] : [];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.addInitScript(() => {
    localStorage.setItem('tottus_token', 'ui-test-token');
    localStorage.setItem('tottus_user', JSON.stringify({ idUsuario: 7, usuario: 'buyer', rol: 'ENCARGADO_COMPRAS', idSede: 1 }));
    localStorage.setItem('tottus_active_sede', '1');
  });
  await page.goto('/ingresos');
  await expect(page.getByRole('heading', { name: 'Ingresos de inventario' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Inventario', exact: true })).toHaveCount(0);
  const productSearch = page.getByRole('combobox', { name: 'Producto' });
  await productSearch.fill('SKU-42');
  await expect(page.getByRole('option')).toContainText('SKU-42');
  await expect(page.locator('.operations-page img')).toHaveCount(0);
  expect(apiCalls).toContain('/api/productos');
  expect(apiCalls).not.toContain('/api/inventario');
});

test('autocompletes by name/SKU, supports keyboard, prevents duplicate details and separates registration from history', async ({ page, request: apiRequest }) => {
  const user = await loginAndOpen(page, '/ingresos');
  const response = await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, {
    headers: { Authorization: `Bearer ${user.token}` }
  });
  const available = (await response.json()).filter((product: any) => product.estado).slice(0, 2);
  expect(available.length).toBeGreaterThanOrEqual(2);

  const firstSearch = page.locator('.product-picker input[role="combobox"]').first();
  await firstSearch.fill(available[0].nombreProducto.slice(0, 5));
  const firstOption = page.getByRole('option').filter({ hasText: available[0].codigoProducto }).first();
  await expect(firstOption).toBeVisible();
  await firstSearch.press('ArrowDown');
  await firstSearch.press('Enter');
  await expect(page.locator('.selected-product').first()).toContainText(available[0].categoria);

  await page.getByRole('button', { name: '+ Agregar producto' }).click();
  const secondSearch = page.locator('.product-picker input[role="combobox"]').nth(1);
  await secondSearch.fill(available[0].codigoProducto);
  await expect(page.getByRole('option').filter({ hasText: available[0].codigoProducto })).toHaveCount(0);
  await expect(page.getByRole('status').filter({ hasText: 'No hay coincidencias' })).toBeVisible();

  await expect(page.getByText('Responsable:')).toBeVisible();
  await expect(page.getByText('Fecha y hora:')).toBeVisible();
  await page.getByRole('tab', { name: 'Historial de ingresos' }).click();
  await expect(page.getByRole('heading', { name: 'Historial de ingresos' })).toBeVisible();
  await page.getByRole('tab', { name: 'Registrar ingreso' }).click();
  await expect(page.getByRole('heading', { name: 'Registrar ingreso' })).toBeVisible();
});
test('el ingreso confirma en un dialogo Angular; cancelar conserva el formulario y no envia HTTP', async ({ page, request: apiRequest }) => {
  const nativeDialogs = watchNativeDialogs(page);
  const user = await loginAndOpen(page, '/ingresos');
  const headers = { Authorization: `Bearer ${user.token}` };
  const inventory = await (await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, { headers })).json();
  const product = inventory.find((item: any) => item.estado);
  expect(product).toBeTruthy();
  const reference = 'PW-CANCEL-' + Date.now();
  await page.getByLabel('Referencia documental').fill(reference);
  await chooseProduct(page, 0, product);
  await page.locator('.quantity-field input').fill('2');
  const writes: string[] = [];
  page.on('request', request => {
    if (request.method() === 'POST' && request.url().endsWith('/api/operaciones/ingresos')) writes.push(request.url());
  });
  const review = page.getByRole('button', { name: 'Registrar ingreso' });
  await review.click();
  const dialog = page.getByRole('dialog', { name: 'Confirmar ingreso de inventario' });
  await expect(dialog).toContainText(reference);
  await expect(dialog).toContainText(product.codigoProducto);
  await expect(dialog).toContainText('Stock actual: ' + product.stockActual);
  await expect(dialog).toContainText('Stock proyectado: ' + (product.stockActual + 2));
  expect(writes).toHaveLength(0);
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(review).toBeFocused();
  await expect(page.getByLabel('Referencia documental')).toHaveValue(reference);
  expect(writes).toHaveLength(0);
  expect(nativeDialogs).toEqual([]);
});

test('el egreso muestra proyeccion y rechaza una cantidad mayor al stock disponible', async ({ page, request: apiRequest }) => {
  const nativeDialogs = watchNativeDialogs(page);
  const user = await loginAndOpen(page, '/egresos');
  const headers = { Authorization: `Bearer ${user.token}` };
  const inventory = await (await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, { headers })).json();
  const product = inventory.find((item: any) => item.estado && item.stockActual > 0);
  expect(product).toBeTruthy();
  await page.getByLabel('Referencia documental').fill('PW-OUT-CHECK-' + Date.now());
  await chooseProduct(page, 0, product);
  const quantity = page.locator('.quantity-field input').first();
  await quantity.fill('1');
  await expect(page.locator('.stock-projection')).toContainText('Stock actual: ' + product.stockActual);
  await expect(page.locator('.stock-projection')).toContainText('Proyección: ' + (product.stockActual - 1));
  const writes: string[] = [];
  page.on('request', request => {
    if (request.method() === 'POST' && request.url().endsWith('/api/operaciones/egresos')) writes.push(request.url());
  });
  await page.getByRole('button', { name: 'Registrar egreso' }).click();
  const dialog = page.getByRole('dialog', { name: 'Confirmar egreso de inventario' });
  await expect(dialog).toContainText('Stock proyectado: ' + (product.stockActual - 1));
  await page.getByRole('button', { name: 'Cancelar' }).click();
  await quantity.fill(String(product.stockActual + 1));
  await page.getByRole('button', { name: 'Registrar egreso' }).click();
  await expect(page.getByRole('alert')).toContainText('Stock insuficiente');
  await expect(dialog).toHaveCount(0);
  expect(writes).toHaveLength(0);
  expect(nativeDialogs).toEqual([]);
});

async function loginAndOpen(page: import('@playwright/test').Page, path: string) {
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
  await page.goto(path);
  await api.dispose();
  return user;
}

function watchNativeDialogs(page: import('@playwright/test').Page): string[] {
  const dialogs: string[] = [];
  page.on('dialog', dialog => { dialogs.push(dialog.type()); void dialog.dismiss(); });
  return dialogs;
}

async function chooseProduct(page: import('@playwright/test').Page, lineIndex: number, product: any) {
  const input = page.locator('.product-picker input[role="combobox"]').nth(lineIndex);
  await input.fill(product.codigoProducto);
  const option = page.getByRole('option').filter({ hasText: `${product.codigoProducto} · ${product.nombreProducto}` }).first();
  await expect(option).toBeVisible();
  await input.press('ArrowDown');
  await input.press('Enter');
  await expect(input).toHaveValue(`${product.codigoProducto} · ${product.nombreProducto}`);
  const row = page.locator('.detail-row').nth(lineIndex);
  await expect(row.locator('.selected-product')).toContainText(product.codigoProducto);
  if (product.categoria) await expect(row.locator('.selected-product')).toContainText(product.categoria);
}

test('HU13-HU15: registra ingreso múltiple y edita conservando la referencia', async ({ page, request: apiRequest }) => {
  const user = await loginAndOpen(page, '/ingresos');
  const inventoryResponse = await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, {
    headers: { Authorization: `Bearer ${user.token}` }
  });
  const products = await inventoryResponse.json();
  const selected = products.filter((p: any, index: number, items: any[]) => p.estado
    && items.findIndex((candidate: any) => candidate.idProducto === p.idProducto) === index
    && items.findIndex((candidate: any) => candidate.codigoProducto === p.codigoProducto) === index).slice(0, 2);
  expect(selected).toHaveLength(2);
  expect(new Set(selected.map((product: any) => product.idProducto)).size).toBe(2);
  const beforeResponse = await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, { headers: { Authorization: `Bearer ${user.token}` } });
  const before = (await beforeResponse.json()).find((p: any) => p.idProducto === selected[0].idProducto).stockActual;
  const ref = `PW-IN-${Date.now()}`;
  await page.getByRole('textbox', { name: /Referencia/ }).fill(ref);
  await chooseProduct(page, 0, selected[0]);
  await page.locator('.quantity-field input').nth(0).fill('2');
  await page.getByRole('button', { name: '+ Agregar producto' }).click();
  await chooseProduct(page, 1, selected[1]);
  await page.locator('.quantity-field input').nth(1).fill('1');
  await expect(page.locator('.selected-product')).toHaveCount(2);
  await expect(page.locator('.quantity-field input').nth(0)).toHaveValue('2');
  await expect(page.locator('.quantity-field input').nth(1)).toHaveValue('1');
  const createResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().endsWith('/api/operaciones/ingresos'));
  await page.getByRole('button', { name: 'Registrar ingreso' }).click();
  await expect(page.getByRole('dialog', { name: 'Confirmar ingreso de inventario' })).toContainText(ref);
  await page.getByRole('button', { name: 'Confirmar ingreso' }).click();
  const createResponse = await createResponsePromise;
  expect(createResponse.ok(), await createResponse.text()).toBeTruthy();
  await page.getByRole('tab', { name: 'Historial de ingresos' }).click();
  await expect(page.getByText(ref)).toBeVisible();
  const after = await (await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, { headers: { Authorization: `Bearer ${user.token}` } })).json();
  expect(after.find((p: any) => p.idProducto === selected[0].idProducto).stockActual).toBe(before + 2);
  await page.getByRole('button', { name: 'Editar ingreso' }).first().click();
  await page.locator('.quantity-field input').nth(0).fill('3');
  const editResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'PUT' && /\/api\/operaciones\/ingresos\/\d+$/.test(response.url()));
  await page.getByRole('button', { name: 'Revisar corrección' }).click();
  await page.getByRole('dialog', { name: 'Confirmar corrección del ingreso' }).getByRole('button', { name: 'Confirmar corrección' }).click();
  const editResponse = await editResponsePromise;
  expect(editResponse.ok(), await editResponse.text()).toBeTruthy();
  await expect(page.getByText(/Kardex conserva los movimientos compensatorios/)).toBeVisible();
});

test('HU16-HU19: registra egreso y actualiza la existencia', async ({ page, request: apiRequest }) => {
  const user = await loginAndOpen(page, '/egresos');
  const inventory = await (await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, { headers: { Authorization: `Bearer ${user.token}` } })).json();
  const item = inventory.find((p: any) => p.estado && p.stockActual > 2);
  const ref = `PW-OUT-${Date.now()}`;
  await page.getByRole('textbox', { name: /Referencia/ }).fill(ref);
  await chooseProduct(page, 0, item);
  await page.locator('.quantity-field input').nth(0).fill('1');
  await page.getByRole('button', { name: 'Registrar egreso' }).click();
  await page.getByRole('dialog', { name: 'Confirmar egreso de inventario' }).getByRole('button', { name: 'Confirmar egreso' }).click();
  await page.getByRole('tab', { name: 'Historial de egresos' }).click();
  await expect(page.getByText(ref)).toBeVisible();
  const movements = await apiRequest.get(`${backend}/api/operaciones/egresos?sedeId=${user.idSede}&query=${ref}`, { headers: { Authorization: `Bearer ${user.token}` } });
  expect((await movements.json()).length).toBe(1);
});

test('un ingreso confirmado actualiza automáticamente el Kardex abierto en otra pestaña', async ({ page, request: apiRequest }) => {
  const user = await loginAndOpen(page, '/ingresos');
  const headers = { Authorization: `Bearer ${user.token}` };
  const inventory = await (await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, { headers })).json();
  const product = inventory.find((item: any) => item.estado);
  expect(product).toBeTruthy();

  const kardexTab = await page.context().newPage();
  await kardexTab.goto('/kardex');
  await expect(kardexTab.getByRole('heading', { name: 'Kardex de inventario' })).toBeVisible();

  const reference = `PW-SYNC-${Date.now()}`;
  await page.getByLabel('Referencia documental').fill(reference);
  await chooseProduct(page, 0, product);
  await page.locator('.quantity-field input').fill('1');
  const kardexReload = kardexTab.waitForResponse(response => response.url().includes('/api/inventario/movimientos') && response.request().method() === 'GET');
  await page.getByRole('button', { name: 'Registrar ingreso' }).click();
  await page.getByRole('button', { name: 'Confirmar ingreso' }).click();
  const reload = await kardexReload;
  expect(reload.ok()).toBeTruthy();
  await expect(kardexTab.locator('tbody')).toContainText(reference);
  await kardexTab.close();
});

test('una vista activa sincroniza cambios de otra sesion con polling moderado', async ({ page, request: apiRequest }) => {
  await page.clock.install();
  const user = await loginAndOpen(page, '/kardex');
  const headers = { Authorization: `Bearer ${user.token}` };
  const inventory = await (await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, { headers })).json();
  const product = inventory.find((item: any) => item.estado);
  expect(product).toBeTruthy();
  const reference = `PW-REMOTE-SYNC-${Date.now()}`;
  const created = await apiRequest.post(`${backend}/api/operaciones/ingresos`, {
    headers,
    data: { idSede: user.idSede, subtipo: 'INGRESO_GENERAL', referencia: reference, detalles: [{ idProducto: product.idProducto, cantidad: 1 }] }
  });
  expect(created.ok(), await created.text()).toBeTruthy();

  const refresh = page.waitForResponse(response => response.url().includes('/api/inventario/movimientos') && response.request().method() === 'GET');
  await page.clock.fastForward(60_000);
  expect((await refresh).ok()).toBeTruthy();
  await expect(page.locator('tbody')).toContainText(reference);
});

test('HU31: presenta métricas y alertas de inventario con acceso de supervisor', async ({ page }) => {
  await loginAndOpen(page, '/dashboard');
  await expect(page.getByRole('heading', { name: 'Dashboard de inventario' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Alertas de stock' })).toBeVisible();
  await expect(page.getByText('Productos activos')).toBeVisible();
  await expect(page.getByText('Unidades disponibles', { exact: true })).toBeVisible();
});

test('HU20: filtra Kardex por tipo, producto y rango de fechas sin modificar stock', async ({ page, request: apiRequest }) => {
  const user = await loginAndOpen(page, '/kardex');
  const headers = { Authorization: `Bearer ${user.token}` };
  const stockBefore = await (await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, { headers })).json();
  const kardexPageResponse = await apiRequest.get(`${backend}/api/inventario/movimientos?sedeId=${user.idSede}&page=0&size=10`, { headers });
  expect(kardexPageResponse.ok()).toBeTruthy();
  const kardexPage = await kardexPageResponse.json();
  expect(Array.isArray(kardexPage.content)).toBeTruthy();
  expect(kardexPage.page).toBe(0);
  expect(kardexPage.size).toBe(10);
  expect(kardexPage.totalElements).toBeGreaterThanOrEqual(kardexPage.content.length);
  const requests: string[] = [];
  page.on('request', r => { if (r.url().includes('/api/inventario/movimientos')) requests.push(r.url()); });
  await expect(page.getByRole('heading', { name: /Kardex de inventario/ })).toBeVisible();
  await page.locator('.kardex-filters select').nth(0).selectOption('ENTRADA');
  await page.locator('.kardex-filters select').nth(1).selectOption({ label: `${stockBefore[0].codigoProducto} · ${stockBefore[0].nombreProducto}` });
  await page.locator('.kardex-filters input[type="date"]').nth(0).fill('2020-01-01');
  await page.locator('.kardex-filters input[type="date"]').nth(1).fill('2030-12-31');
  await expect.poll(() => requests.some(url => {
    const params = new URL(url).searchParams;
    return params.get('tipo') === 'ENTRADA'
      && params.get('productoId') === String(stockBefore[0].idProducto)
      && params.get('desde') === '2020-01-01'
      && params.get('hasta') === '2030-12-31';
  })).toBeTruthy();
  const last = new URL(requests[requests.length - 1]);
  expect(last.searchParams.get('tipo')).toBe('ENTRADA');
  expect(last.searchParams.get('productoId')).toBe(String(stockBefore[0].idProducto));
  expect(last.searchParams.get('desde')).toBe('2020-01-01');
  expect(last.searchParams.get('hasta')).toBe('2030-12-31');
  const stockAfter = await (await apiRequest.get(`${backend}/api/inventario?sedeId=${user.idSede}`, { headers })).json();
  expect(stockAfter.map((i: any) => i.stockActual)).toEqual(stockBefore.map((i: any) => i.stockActual));
});
