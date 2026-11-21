import { expect, test } from '@playwright/test';

const products = [
  { idInventario: 11, idProducto: 101, idSede: 1, codigoProducto: 'SKU-001', nombreProducto: 'Arroz', categoria: 'Abarrotes', precio: 1, stockActual: 20, stockMinimo: 2, estado: true, estadoStock: 'OPTIMO', ubicacion: 'Pasillo 1', nombreSede: 'Tottus MegaPlaza' },
  { idInventario: 12, idProducto: 102, idSede: 1, codigoProducto: 'SKU-002', nombreProducto: 'Azúcar', categoria: 'Abarrotes', precio: 1, stockActual: 20, stockMinimo: 2, estado: true, estadoStock: 'OPTIMO', ubicacion: 'Pasillo 1', nombreSede: 'Tottus MegaPlaza' },
  { idInventario: 13, idProducto: 103, idSede: 1, codigoProducto: 'SKU-003', nombreProducto: 'Aceite', categoria: 'Abarrotes', precio: 1, stockActual: 20, stockMinimo: 2, estado: true, estadoStock: 'OPTIMO', ubicacion: 'Pasillo 1', nombreSede: 'Tottus MegaPlaza' },
  { idInventario: 14, idProducto: 104, idSede: 1, codigoProducto: 'SKU-004', nombreProducto: 'Leche', categoria: 'Lácteos', precio: 1, stockActual: 20, stockMinimo: 2, estado: true, estadoStock: 'OPTIMO', ubicacion: 'Pasillo 1', nombreSede: 'Tottus MegaPlaza' },
  { idInventario: 15, idProducto: 105, idSede: 1, codigoProducto: 'SKU-005', nombreProducto: 'Fideos', categoria: 'Abarrotes', precio: 1, stockActual: 20, stockMinimo: 2, estado: true, estadoStock: 'OPTIMO', ubicacion: 'Pasillo 1', nombreSede: 'Tottus MegaPlaza' }
];

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/inventario') {
      await route.fulfill({ json: products });
    } else if (path === '/api/sedes') {
      await route.fulfill({ json: [{ idSede: 1, nombre: 'Tottus MegaPlaza' }] });
    } else if (path === '/api/operaciones/ingresos' && request.method() === 'GET') {
      await route.fulfill({ json: [] });
    } else if (path === '/api/operaciones/ingresos' && request.method() === 'POST') {
      await route.fulfill({ status: 201, json: { idOperacion: 1 } });
    } else {
      await route.fulfill({ json: [] });
    }
  });
  await page.addInitScript(() => {
    localStorage.setItem('tottus_token', 'autocomplete-fixture');
    localStorage.setItem('tottus_user', JSON.stringify({ idUsuario: 4, usuario: 'operador', rol: 'OPERADOR_ALMACEN', idSede: 1, nombreSede: 'Tottus MegaPlaza' }));
    localStorage.setItem('tottus_active_sede', '1');
  });
  await page.goto('/ingresos');
  await expect(page.getByRole('heading', { name: 'Ingresos de inventario' })).toBeVisible();
});

test('selecciona por clic en varias filas, reemplaza la selección y envía los IDs exactos', async ({ page }) => {
  const payloads: unknown[] = [];
  await page.route('**/api/operaciones/ingresos', async route => {
    const request = route.request();
    if (request.method() !== 'POST') return route.fulfill({ json: [] });
    payloads.push(request.postDataJSON());
    return route.fulfill({ status: 201, json: { idOperacion: 1 } });
  });

  await page.getByLabel('Referencia documental').fill('AUTOCOMPLETE-IDS');
  await expect(page.getByText(/Ingreso general:/)).toBeVisible();
  await expect(page.getByRole('radio')).toHaveCount(0);
  await chooseByClick(page, 0, products[0]);
  await chooseByClick(page, 0, products[1]);
  await page.locator('.quantity-field input').nth(0).fill('2');
  await page.getByRole('button', { name: '+ Agregar producto' }).click();
  await chooseByClick(page, 1, products[2]);
  await page.locator('.quantity-field input').nth(1).fill('3');
  await page.getByRole('button', { name: '+ Agregar producto' }).click();
  await chooseByClick(page, 2, products[4]);
  await page.locator('.quantity-field input').nth(2).fill('4');

  const review = page.getByRole('button', { name: 'Registrar ingreso' });
  await review.click();
  const dialog = page.getByRole('dialog', { name: 'Confirmar ingreso de inventario' });
  await expect(dialog.getByText('SKU-002 · Azúcar')).toBeVisible();
  await expect(dialog.getByText('SKU-003 · Aceite')).toBeVisible();
  await expect(dialog.getByText('SKU-005 · Fideos')).toBeVisible();
  await dialog.getByRole('button', { name: 'Confirmar ingreso' }).click();

  await expect.poll(() => payloads.length).toBe(1);
  const payload = payloads[0] as { subtipo: string; detalles: Array<{ idProducto: number; cantidad: number }> };
  expect(payload.subtipo).toBe('INGRESO_GENERAL');
  expect(payload.detalles).toEqual([
    { idProducto: 102, cantidad: 2 },
    { idProducto: 103, cantidad: 3 },
    { idProducto: 105, cantidad: 4 }
  ]);
});

test('Enter no selecciona el primer resultado si no hay una opción resaltada', async ({ page }) => {
  const input = page.getByRole('combobox', { name: 'Producto' });
  await input.fill('SKU-');
  await expect(page.getByRole('listbox').getByRole('option')).toHaveCount(5);
  await input.press('Enter');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.selected-product')).toHaveCount(0);

  await input.press('ArrowUp');
  await expect(input).toHaveAttribute('aria-activedescendant', 'product-option-0-105');
  await input.press('Enter');
  await expect(input).toHaveValue('SKU-005 · Fideos');
});

for (const subtype of ['VENTA', 'MERMA'] as const) {
  test(`egreso ${subtype} conserva el producto de la fila y el ID enviado`, async ({ page }) => {
    let submitted: { subtipo: string; detalles: Array<{ idProducto: number; cantidad: number }> } | null = null;
    await page.goto('/egresos');
    await page.route('**/api/operaciones/egresos', async route => {
      if (route.request().method() !== 'POST') return route.fallback();
      submitted = route.request().postDataJSON();
      return route.fulfill({ status: 201, json: { idOperacion: 9 } });
    });
    await expect(page.getByRole('heading', { name: 'Egresos de inventario' })).toBeVisible();
    await page.getByLabel('Referencia documental').fill(`EGRESO-${subtype}`);
    const input = page.getByRole('combobox', { name: 'Producto' }).first();
    await input.fill('Aceite');
    await page.getByRole('option', { name: /SKU-003.*Aceite/ }).click();
    await expect(input).toHaveValue('SKU-003 · Aceite');
    await page.locator('.quantity-field input').first().fill('2');
    if (subtype === 'MERMA') await page.getByRole('radio', { name: 'Merma' }).check();
    await page.getByRole('button', { name: 'Registrar egreso' }).click();
    const dialog = page.getByRole('dialog', { name: 'Confirmar egreso de inventario' });
    await expect(dialog).toContainText('SKU-003 · Aceite');
    await dialog.getByRole('button', { name: 'Confirmar egreso' }).click();
    await expect.poll(() => submitted).not.toBeNull();
    expect(submitted).toEqual({ subtipo: subtype, referencia: `EGRESO-${subtype}`, idSede: 1, detalles: [{ idProducto: 103, cantidad: 2 }] });
  });
}

async function chooseByClick(page: import('@playwright/test').Page, rowIndex: number, product: typeof products[number]): Promise<void> {
  const input = page.getByRole('combobox', { name: 'Producto' }).nth(rowIndex);
  await input.fill('SKU-');
  const option = page.getByRole('option', { name: new RegExp(`${product.codigoProducto}.*${product.nombreProducto}`) });
  await expect(option).toBeVisible();
  await option.click();
  await expect(input).toHaveValue(`${product.codigoProducto} · ${product.nombreProducto}`);
}
