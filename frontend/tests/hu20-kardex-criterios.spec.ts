import { test, expect } from '@playwright/test';

test.use({ storageState: 'playwright/.auth/tottus.json' });

test('P2-01 | HU20 | Consulta independiente del Kardex, paginada y limitada a sede', async ({ page }) => {
  const requests: string[] = [];
  page.on('request', request => {
    if (request.method() === 'GET' && request.url().includes('/api/inventario/movimientos')) requests.push(request.url());
  });

  const responsePromise = page.waitForResponse(response =>
    response.request().method() === 'GET' && response.url().includes('/api/inventario/movimientos'));
  await page.goto('http://localhost:4200/kardex');
  await expect(page.getByRole('heading', { name: 'Kardex de inventario' })).toBeVisible();
  const response = await responsePromise;
  expect(response.ok()).toBeTruthy();
  const url = new URL(response.url());
  expect(url.searchParams.get('sedeId')).toBeTruthy();
  expect(url.searchParams.get('page')).toBe('0');
  expect(url.searchParams.get('size')).toBe('20');

  const data: { content: Array<{ idSede: number; fecha: string; tipo: string; codigoProducto: string; nombreProducto: string; cantidad: number; referencia: string }>; page: number; size: number; totalElements: number; totalPages: number } = await response.json();
  expect(data.page).toBe(0);
  expect(data.size).toBe(20);
  expect(data.totalElements).toBeGreaterThanOrEqual(data.content.length);
  expect(data.content.every(movement => String(movement.idSede) === url.searchParams.get('sedeId'))).toBeTruthy();

  const table = page.locator('.kardex-table');
  for (const label of ['Fecha y hora', 'Tipo', 'Producto', 'Cantidad', 'Responsable', 'Referencia / motivo']) {
    await expect(table.getByRole('columnheader', { name: label })).toBeVisible();
  }
  if (!data.content.length) {
    await expect(page.getByText('No hay movimientos para los filtros seleccionados.')).toBeVisible();
  } else {
    const rows = table.locator('tbody tr');
    await expect(rows).toHaveCount(data.content.length);
    await expect(rows.first()).toContainText(data.content[0].codigoProducto);
    await expect(rows.first()).toContainText(data.content[0].referencia);
  }
  expect(requests.length).toBeGreaterThan(0);
  expect(requests.every(request => new URL(request).searchParams.has('sedeId'))).toBeTruthy();
  await expect(page.getByRole('link', { name: 'Volver al catálogo' })).toBeVisible();
});
